/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;

import org.jboss.logging.Logger;
import org.jboss.remoting3.security.PasswordClientCallbackHandler;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RegisteredService;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoFuture.HandlingNotifier;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;
import org.xnio.ssl.JsseXnioSsl;
import org.xnio.ssl.XnioSsl;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").infof("JBoss Remoting version %s", Version.getVersionString());
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private static final RemotingPermission REGISTER_SERVICE_PERM = new RemotingPermission("registerService");
    private static final RemotingPermission CONNECT_PERM = new RemotingPermission("connect");
    private static final RemotingPermission ADD_CONNECTION_PROVIDER_PERM = new RemotingPermission("addConnectionProvider");
    private static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE_PERM = new RemotingPermission("getConnectionProviderInterface");
    private static final int CLOSED_FLAG = 0x80000000;
    private static final int COUNT_MASK = ~(CLOSED_FLAG);
    private static final String FQCN = EndpointImpl.class.getName();

    private final Set<ConnectionImpl> connections = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ConnectionImpl, Boolean>()));

    private final Attachments attachments = new Attachments();

    private final ConcurrentMap<String, ConnectionProvider> connectionProviders = new UnlockedReadHashMap<String, ConnectionProvider>();
    private final ConcurrentMap<String, RegisteredServiceImpl> registeredServices = new UnlockedReadHashMap<String, RegisteredServiceImpl>();

    private final Xnio xnio;
    private final XnioWorker worker;

    private final Object connectionLock = new Object();

    private static final AtomicIntegerFieldUpdater<EndpointImpl> resourceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(EndpointImpl.class, "resourceCount");

    @SuppressWarnings("unused")
    private volatile int resourceCount = 0;

    private static final Pattern VALID_SERVICE_PATTERN = Pattern.compile("[-.:a-zA-Z_0-9]+");

    /**
     * The name of this endpoint.
     */
    private final String name;
    @SuppressWarnings("unused")
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;
    private final CloseHandler<Object> resourceCloseHandler = new CloseHandler<Object>() {
        public void handleClose(final Object closed, final IOException exception) {
            closeTick1(closed);
        }
    };
    private final EndpointImpl.ConnectionCloseHandler connectionCloseHandler = new EndpointImpl.ConnectionCloseHandler();

    private EndpointImpl(final Xnio xnio, final XnioWorker xnioWorker, final String name, final OptionMap optionMap, final HoldingRunnable holdingRunnable) throws IOException {
        super(xnioWorker, true);
        holdingRunnable.setTask(new Runnable() {
            public void run() {
                closeComplete();
            }
        });
        worker = xnioWorker;
        this.xnio = xnio;
        this.name = name;
        this.optionMap = optionMap;
        // initialize CPC
        connectionProviderContext = new ConnectionProviderContextImpl();
        // add default connection providers
        connectionProviders.put("local", new LocalConnectionProvider(connectionProviderContext, worker));
        // get XNIO worker
        log.tracef("Completed open of %s", this);
    }

    static EndpointImpl construct(final Xnio xnio, final String name, final OptionMap optionMap) throws IOException {
        final HoldingRunnable holdingRunnable = new HoldingRunnable();
        final XnioWorker xnioWorker = xnio.createWorker(null, OptionMap.builder().addAll(optionMap).set(Options.WORKER_NAME, name == null ? "Remoting (anonymous)" : "Remoting \"" + name + "\"").getMap(), holdingRunnable);
        return new EndpointImpl(xnio, xnioWorker, name, optionMap, holdingRunnable);
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String getName() {
        return name;
    }

    public Executor getExecutor() {
        return new TrackingExecutor();
    }

    private void closeTick1(Object c) {
        int res = resourceCountUpdater.decrementAndGet(this);
        if (res == CLOSED_FLAG) {
            // this was the last phase 1 resource.
            finishPhase1();
        } else if ((res & CLOSED_FLAG) != 0) {
            // shutdown is currently in progress.
            if (log.isTraceEnabled()) {
                log.logf(FQCN, Logger.Level.TRACE, null, "Phase 1 shutdown count %08x of %s (closed %s)", Integer.valueOf(res & COUNT_MASK), this, c);
            }
        } else {
            if (log.isTraceEnabled()) {
                log.logf(FQCN, Logger.Level.TRACE, null, "Resource closed count %08x of %s (closed %s)", Integer.valueOf(res & COUNT_MASK), this, c);
            }
        }
    }

    private void finishPhase1() {
        // all our original resources were closed; now move on to stage two (thread pools)
        log.tracef("Finished phase 1 shutdown of %s", this);
        worker.shutdown();
        return;
    }

    void resourceUntick(Object opened) throws NotOpenException {
        int old;
        do {
            old = resourceCountUpdater.get(this);
            if ((old & CLOSED_FLAG) != 0) {
                throw new NotOpenException("Endpoint is not open");
            }
        } while (! resourceCountUpdater.compareAndSet(this, old, old + 1));
        if (log.isTraceEnabled()) {
            log.tracef("Allocated tick to %d of %s (opened %s)", Integer.valueOf(old + 1), this, opened);
        }
    }


    void executorUntick(Object opened) {
        // just like resourceUntick - except we allow tasks to be submitted after close begins.
        int old;
        do {
            old = resourceCountUpdater.get(this);
            if (old == CLOSED_FLAG) {
                throw new RejectedExecutionException("Endpoint is not open");
            }
        } while (! resourceCountUpdater.compareAndSet(this, old, old + 1));
        if (log.isTraceEnabled()) {
            log.tracef("Allocated tick to %d of %s (opened %s)", Integer.valueOf(old + 1), this, opened);
        }
    }

    boolean isCloseFlagSet() {
        return Bits.allAreSet(resourceCountUpdater.get(this), CLOSED_FLAG);
    }

    protected void closeAction() throws IOException {
        synchronized (connectionLock) {
            // Commence phase one shutdown actions
            int res;
            do {
                res = resourceCount;
            } while (! resourceCountUpdater.compareAndSet(this, res, res | CLOSED_FLAG));
            if (res == 0) {
                finishPhase1();
            } else {
                for (Object connection : connections.toArray()) {
                    ((ConnectionImpl)connection).closeAsync();
                }
                for (ConnectionProvider connectionProvider : connectionProviders.values()) {
                    connectionProvider.closeAsync();
                }
            }
        }
    }

    public Registration registerService(final String serviceType, final OpenListener openListener, final OptionMap optionMap) throws ServiceRegistrationException {
        if (! VALID_SERVICE_PATTERN.matcher(serviceType).matches()) {
            throw new IllegalArgumentException("Service type must match " + VALID_SERVICE_PATTERN);
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        final RegisteredServiceImpl registeredService = new RegisteredServiceImpl(openListener, optionMap);
        if (registeredServices.putIfAbsent(serviceType, registeredService) != null) {
            throw new ServiceRegistrationException("Service type '" + serviceType + "' is already registered");
        }
        final MapRegistration<RegisteredServiceImpl> registration = new MapRegistration<RegisteredServiceImpl>(registeredServices, serviceType, registeredService) {
            protected void closeAction() throws IOException {
                try {
                    openListener.registrationTerminated();
                } finally {
                    super.closeAction();
                }
            }
        };
        // automatically close the registration when the endpoint is closed
        final Key key = addCloseHandler(SpiUtils.closingCloseHandler(registration));
        registration.addCloseHandler(new CloseHandler<Registration>() {
            public void handleClose(final Registration closed, final IOException exception) {
                key.remove();
            }
        });
        return registration;
    }

    private IoFuture<Connection> doConnect(final URI uri, final OptionMap connectOptions, final CallbackHandler callbackHandler, final XnioSsl xnioSsl) throws IOException {
        final String scheme = uri.getScheme();
        final String destinationHost = uri.getHost();
        final SocketAddress destination;
        if (destinationHost != null) {
            final int destinationPort = uri.getPort();
            destination = new InetSocketAddress(destinationHost, destinationPort == -1 ? 0 : destinationPort);
        } else {
            destination = null;
        }
        return doConnect(scheme, null, destination, connectOptions, callbackHandler, xnioSsl);
    }

    private IoFuture<Connection> doConnect(final String scheme, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final CallbackHandler callbackHandler, final XnioSsl xnioSsl) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        synchronized (connectionLock) {
            boolean ok = false;
            resourceUntick("Connection to " + destination);
            try {
                final ConnectionProvider connectionProvider = connectionProviders.get(scheme);
                if (connectionProvider == null) {
                    throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
                }
                final FutureResult<Connection> futureResult = new FutureResult<Connection>(getExecutor());
                final FutureResult<ConnectionHandlerFactory> connHandlerFuture = new FutureResult<ConnectionHandlerFactory>();
                // Mark the stack because otherwise debugging connect problems can be incredibly tough
                final StackTraceElement[] mark = Thread.currentThread().getStackTrace();
                connHandlerFuture.getIoFuture().addNotifier(new HandlingNotifier<ConnectionHandlerFactory, Void>() {

                    public void handleCancelled(final Void attachment) {
                        log.logf(getClass().getName(), Logger.Level.TRACE, null, "Registered cancellation result");
                        closeTick1("a cancelled connection");
                        futureResult.setCancelled();
                    }

                    public void handleFailed(final IOException exception, final Void attachment) {
                        log.logf(getClass().getName(), Logger.Level.TRACE, exception, "Registered exception result");
                        closeTick1("a failed connection (2)");
                        SpiUtils.glueStackTraces(exception, mark, 1, "asynchronous invocation");
                        futureResult.setException(exception);
                    }

                    public void handleDone(final ConnectionHandlerFactory connHandlerFactory, final Void attachment) {
                        worker.execute(new Runnable() {
                            @Override
                            public void run() {
                                log.logf(getClass().getName(), Logger.Level.TRACE, null, "Registered successful result %s", connHandlerFactory);
                                synchronized (EndpointImpl.this.connectionLock) {
                                    final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connHandlerFactory, connectionProviderContext);
                                    connection.getConnectionHandler().addCloseHandler(SpiUtils.asyncClosingCloseHandler(connection));
                                    connection.addCloseHandler(resourceCloseHandler);
                                    connection.addCloseHandler(connectionCloseHandler);
                                    // see if we were closed in the meantime
                                    if (EndpointImpl.this.isCloseFlagSet()) {
                                        IoUtils.safeClose(connection);
                                        futureResult.setCancelled();
                                    } else {
                                        connections.add(connection);
                                        futureResult.setResult(connection);
                                    }                            
                                } 
                            }
                        });

                    }
                }, null);
                final Cancellable connect = connectionProvider.connect(bindAddress, destination, connectOptions,  connHandlerFuture, callbackHandler, xnioSsl);
                ok = true;
                futureResult.addCancelHandler(connect);
                return futureResult.getIoFuture();
            } finally {
                if (! ok) {
                    closeTick1("a failed connection (1)");
                }
            }
        }
    }

    public IoFuture<Connection> connect(final URI destination) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder();
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null), null);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null), null);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        return connect(destination, connectOptions, callbackHandler, (XnioSsl) null);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler, final SSLContext sslContext) throws IOException {
        return connect(destination, connectOptions, callbackHandler, sslContext == null ? (XnioSsl) null : new JsseXnioSsl(xnio, optionMap, sslContext));
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler, final XnioSsl xnioSsl) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, callbackHandler, xnioSsl);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password) throws IOException {
        return connect(destination, connectOptions, userName, realmName, password, (XnioSsl) null);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password, final SSLContext sslContext) throws IOException {
        return connect(destination, connectOptions, userName, realmName, password, sslContext == null ? (XnioSsl) null : new JsseXnioSsl(xnio, optionMap, sslContext));
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password, final XnioSsl xnioSsl) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final String actualUserName = userName != null ? userName : uriUserName != null ? uriUserName : connectOptions.get(RemotingOptions.AUTHORIZE_ID);
        final String actualUserRealm = realmName != null ? realmName : uriUserRealm != null ? uriUserRealm : connectOptions.get(RemotingOptions.AUTH_REALM);
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (actualUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, actualUserName);
        if (actualUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, actualUserRealm);
        final OptionMap finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(actualUserName, actualUserRealm, password), xnioSsl);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination) throws IOException {
        return doConnect(protocol, bindAddress, destination, OptionMap.EMPTY, null, null);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions) throws IOException {
        return doConnect(protocol, bindAddress, destination, connectOptions, null, null);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        return doConnect(protocol, bindAddress, destination, connectOptions, callbackHandler, null);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final CallbackHandler callbackHandler, final SSLContext sslContext) throws IOException {
        return doConnect(protocol, bindAddress, destination, connectOptions, callbackHandler, sslContext == null ? (XnioSsl) null : new JsseXnioSsl(xnio, optionMap, sslContext));
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final CallbackHandler callbackHandler, final XnioSsl xnioSsl) throws IOException {
        return doConnect(protocol, bindAddress, destination, connectOptions, callbackHandler, xnioSsl);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, String userName, String realmName, final char[] password) throws IOException {
        return connect(protocol, bindAddress, destination, connectOptions, userName, realmName, password, (XnioSsl) null);
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password, final SSLContext sslContext) throws IOException {
        return connect(protocol, bindAddress, destination, connectOptions, userName, realmName, password, sslContext == null ? (XnioSsl) null : new JsseXnioSsl(xnio, optionMap, sslContext));
    }

    public IoFuture<Connection> connect(final String protocol, final SocketAddress bindAddress, final SocketAddress destination, final OptionMap connectOptions, String userName, String realmName, final char[] password, final XnioSsl xnioSsl) throws IOException {
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (userName != null) builder.set(RemotingOptions.AUTHORIZE_ID, userName); else userName = optionMap.get(RemotingOptions.AUTHORIZE_ID);
        if (realmName != null) builder.set(RemotingOptions.AUTH_REALM, realmName); else realmName = optionMap.get(RemotingOptions.AUTH_REALM);
        final OptionMap finalMap = builder.getMap();
        return doConnect(protocol, bindAddress, destination, finalMap, new PasswordClientCallbackHandler(userName, realmName, password), xnioSsl);
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        boolean ok = false;
        resourceUntick("Connection provider for " + uriScheme);
        try {
            final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl();
            final ConnectionProvider provider = providerFactory.createInstance(context, optionMap);
            try {
                if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
                    throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
                }
                // add a resource count for close
                log.tracef("Adding connection provider registration named '%s': %s", uriScheme, provider);
                final Registration registration = new MapRegistration<ConnectionProvider>(connectionProviders, uriScheme, provider) {
                    protected void closeAction() throws IOException {
                        try {
                            provider.closeAsync();
                        } finally {
                            super.closeAction();
                        }
                    }
                };
                provider.addCloseHandler(new CloseHandler<ConnectionProvider>() {
                    public void handleClose(final ConnectionProvider closed, final IOException exception) {
                        registration.closeAsync();
                        closeTick1(closed);
                    }
                });
                ok = true;
                return registration;
            } finally {
                if (! ok) {
                    provider.close();
                }
            }
        } finally {
            if (! ok) {
                closeTick1("Connection provider for " + uriScheme);
            }
        }
    }

    public <T> T getConnectionProviderInterface(final String uriScheme, final Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(GET_CONNECTION_PROVIDER_INTERFACE_PERM);
        }
        if (! expectedType.isInterface()) {
            throw new IllegalArgumentException("Interface expected");
        }
        final ConnectionProvider provider = connectionProviders.get(uriScheme);
        if (provider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + uriScheme + "\" is installed");
        }
        return expectedType.cast(provider.getProviderInterface());
    }

    public boolean isValidUriScheme(final String uriScheme) {
        return connectionProviders.containsKey(uriScheme);
    }

    public XnioWorker getXnioWorker() {
        return worker;
    }

    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("endpoint ");
        if (name != null) {
            b.append('"').append(name).append('"');
        } else {
            b.append("(anonymous)");
        }
        b.append(" <").append(Integer.toHexString(hashCode())).append(">");
        return b.toString();
    }

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private static String uriDecode(String encoded) {
        final char[] chars = encoded.toCharArray();
        final int olen = chars.length;
        final byte[] buf = new byte[olen];
        int c = 0;
        for (int i = 0; i < olen; i++) {
            final char ch = chars[i];
            if (ch == '%') {
                buf[c++] = (byte) (Character.digit(chars[++i], 16) << 4 | Character.digit(chars[++i], 16));
            } else if (ch < 32 || ch > 127) {
                // skip it
            } else {
                buf[c++] = (byte) ch;
            }
        }
        return new String(buf, 0, c, UTF_8);
    }

    static final class UserAndRealm {
        private final String user;
        private final String realm;

        UserAndRealm(final String user, final String realm) {
            this.user = user;
            this.realm = realm;
        }

        public String getUser() {
            return user;
        }

        public String getRealm() {
            return realm;
        }
    }

    private static final UserAndRealm EMPTY = new UserAndRealm(null, null);

    private UserAndRealm getUserAndRealm(URI uri) {
        final String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            return EMPTY;
        }
        int i = userInfo.indexOf(';');
        if (i == -1) {
            return new UserAndRealm(uri.getUserInfo(), null);
        } else {
            return new UserAndRealm(uriDecode(userInfo.substring(0, i)), uriDecode(userInfo.substring(i + 1)));
        }
    }

    private class MapRegistration<T> extends AbstractHandleableCloseable<Registration> implements Registration {

        private final ConcurrentMap<String, T> map;
        private final String key;
        private final T value;

        private MapRegistration(final ConcurrentMap<String, T> map, final String key, final T value) {
            super(EndpointImpl.this.getExecutor(), false);
            this.map = map;
            this.key = key;
            this.value = value;
        }

        protected void closeAction() throws IOException {
            map.remove(key, value);
            closeComplete();
        }

        public void close() {
            try {
                super.close();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        public String toString() {
            return String.format("Registration of '%s': %s", key, value);
        }
    }

    final class LocalConnectionContext implements ConnectionHandlerContext {
        private final ConnectionProviderContext connectionProviderContext;
        private final Connection connection;

        LocalConnectionContext(final ConnectionProviderContext connectionProviderContext, final Connection connection) {
            this.connectionProviderContext = connectionProviderContext;
            this.connection = connection;
        }

        public ConnectionProviderContext getConnectionProviderContext() {
            return connectionProviderContext;
        }

        @Deprecated
        public OpenListener getServiceOpenListener(final String serviceType) {
            final RegisteredServiceImpl registeredService = registeredServices.get(serviceType);
            return registeredService == null ? null : registeredService.getOpenListener();
        }

        public RegisteredServiceImpl getRegisteredService(final String serviceType) {
            return registeredServices.get(serviceType);
        }

        public Connection getConnection() {
            return connection;
        }

        public void remoteClosed() {
            connection.closeAsync();
        }
    }

    private final class ConnectionProviderContextImpl implements ConnectionProviderContext {

        private ConnectionProviderContextImpl() {
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            synchronized (connectionLock) {
                try {
                    resourceUntick("an inbound connection");
                } catch (NotOpenException e) {
                    throw new IllegalStateException("Accept after endpoint close", e);
                }
                boolean ok = false;
                try {
                    final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this);
                    connections.add(connection);
                    connection.getConnectionHandler().addCloseHandler(SpiUtils.asyncClosingCloseHandler(connection));
                    connection.addCloseHandler(connectionCloseHandler);
                    connection.addCloseHandler(resourceCloseHandler);
                    ok = true;
                } finally {
                    if (! ok) closeTick1("a failed inbound connection");
                }
            }
        }

        public Endpoint getEndpoint() {
            return EndpointImpl.this;
        }

        public Xnio getXnio() {
            return xnio;
        }

        public Executor getExecutor() {
            return EndpointImpl.this.getExecutor();
        }

        public XnioWorker getXnioWorker() {
            return worker;
        }
    }

    private class ConnectionCloseHandler implements CloseHandler<Connection> {

        public void handleClose(final Connection closed, final IOException exception) {
            connections.remove(closed);
        }
    }

    private static class HoldingRunnable implements Runnable {
        private volatile Runnable task;

        private HoldingRunnable() {
        }

        public void setTask(final Runnable task) {
            this.task = task;
        }

        public void run() {
            final Runnable task = this.task;
            if (task != null) task.run();
        }
    }

    private static class RegisteredServiceImpl implements RegisteredService {
        private final OpenListener openListener;
        private final OptionMap optionMap;

        private RegisteredServiceImpl(final OpenListener openListener, final OptionMap optionMap) {
            this.openListener = openListener;
            this.optionMap = optionMap;
        }

        public OpenListener getOpenListener() {
            return openListener;
        }

        public OptionMap getOptionMap() {
            return optionMap;
        }
    }

    private final class TrackingExecutor implements Executor {
        private final AtomicInteger count = new AtomicInteger();

        public void execute(final Runnable command) {
            final AtomicInteger count = this.count;
            final int i = count.getAndIncrement();
            boolean ok = false;
            try {
                if (i == 0) {
                    executorUntick(this);
                }
                worker.execute(new Runnable() {
                    public void run() {
                        try {
                            command.run();
                        } finally {
                            finishWork();
                        }
                    }
                });
                ok = true;
            } finally {
                if (! ok) {
                    finishWork();
                }
            }
        }

        void finishWork() {
            if (count.decrementAndGet() == 0) {
                closeTick1(this);
            }
        }
    }
}

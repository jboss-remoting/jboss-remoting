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
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.jboss.remoting3.security.PasswordClientCallbackHandler;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.SpiUtils;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Result;
import org.jboss.logging.Logger;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").infof("JBoss Remoting version %s", Version.VERSION);
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private static final RemotingPermission REGISTER_SERVICE_PERM = new RemotingPermission("registerService");
    private static final RemotingPermission CREATE_CHANNEL_PERM = new RemotingPermission("createChannel");
    private static final RemotingPermission CONNECT_PERM = new RemotingPermission("connect");
    private static final RemotingPermission ADD_CONNECTION_PROVIDER_PERM = new RemotingPermission("addConnectionProvider");
    private static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE_PERM = new RemotingPermission("getConnectionProviderInterface");

    private final Attachments attachments = new Attachments();

    private final ConcurrentMap<String, ConnectionProvider> connectionProviders = new UnlockedReadHashMap<String, ConnectionProvider>();
    private final ConcurrentMap<String, OpenListener> registeredServices = new UnlockedReadHashMap<String, OpenListener>();

    private static final Pattern VALID_SERVICE_PATTERN = Pattern.compile("[-.:a-zA-Z_0-9]+");

    /**
     * The name of this endpoint.
     */
    private final String name;
    @SuppressWarnings("unused")
    private final OptionMap optionMap;
    private final ConnectionProviderContext connectionProviderContext;

    EndpointImpl(final Executor executor, final String name, final OptionMap optionMap) throws IOException {
        super(executor);
        this.executor = executor;
        this.name = name;
        connectionProviderContext = new ConnectionProviderContextImpl();
        // add default connection providers
        connectionProviders.put("local", new LocalConnectionProvider(connectionProviderContext, executor));
        // add default services
        this.optionMap = optionMap;
    }

    private final Executor executor;

    protected Executor getExecutor() {
        return executor;
    }

    public Attachments getAttachments() {
        return attachments;
    }

    public String getName() {
        return name;
    }

    public ChannelPair createChannelPair() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_CHANNEL_PERM);
        }
        final LoopbackChannel channel = new LoopbackChannel(executor);
        return new ChannelPair(channel, channel.getOtherSide());
    }

    public Registration registerService(final String serviceType, final OpenListener openListener, final OptionMap optionMap) throws ServiceRegistrationException {
        if (! VALID_SERVICE_PATTERN.matcher(serviceType).matches()) {
            throw new IllegalArgumentException("Service type must match " + VALID_SERVICE_PATTERN);
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(REGISTER_SERVICE_PERM);
        }
        final OpenListener existing = registeredServices.putIfAbsent(serviceType, openListener);
        if (existing != null) {
            throw new ServiceRegistrationException("Service type '" + serviceType + "' is already registered");
        }
        final MapRegistration<OpenListener> registration = new MapRegistration<OpenListener>(registeredServices, serviceType, openListener) {
            protected void closeAction() {
                super.closeAction();
                openListener.registrationTerminated();
            }
        };
        // automatically close the registration when the endpoint is closed
        final Key key = addCloseHandler(SpiUtils.closingCloseHandler(registration));
        registration.addCloseHandler(new CloseHandler<Registration>() {
            public void handleClose(final Registration closed) {
                key.remove();
            }
        });
        return registration;
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
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null));
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
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(finalMap.get(RemotingOptions.AUTHORIZE_ID), finalMap.get(RemotingOptions.AUTH_REALM), null));
    }

    private IoFuture<Connection> doConnect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CONNECT_PERM);
        }
        final String scheme = destination.getScheme();
        final ConnectionProvider connectionProvider = connectionProviders.get(scheme);
        if (connectionProvider == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
        }
        final FutureResult<Connection> futureResult = new FutureResult<Connection>(executor);
        // Mark the stack because otherwise debugging connect problems can be incredibly tough
        final StackTraceElement[] mark = Thread.currentThread().getStackTrace();
        futureResult.addCancelHandler(connectionProvider.connect(destination, connectOptions, new Result<ConnectionHandlerFactory>() {
            public boolean setResult(final ConnectionHandlerFactory result) {
                return futureResult.setResult(new ConnectionImpl(EndpointImpl.this, result, connectionProviderContext, destination.toString()));
            }

            public boolean setException(final IOException exception) {
                glueStackTraces(exception, mark, 1, "asynchronous invocation");
                return futureResult.setException(exception);
            }

            public boolean setCancelled() {
                return futureResult.setCancelled();
            }
        }, callbackHandler));
        return futureResult.getIoFuture();
    }

    static void glueStackTraces(final Throwable exception, final StackTraceElement[] ust, final int trimCount, final String msg) {
        final StackTraceElement[] est = exception.getStackTrace();
        final StackTraceElement[] fst = Arrays.copyOf(est, est.length + ust.length);
        fst[est.length] = new StackTraceElement("..." + msg + "..", "", null, -1);
        System.arraycopy(ust, trimCount, fst, est.length + 1, ust.length - trimCount);
        exception.setStackTrace(fst);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final CallbackHandler callbackHandler) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final OptionMap finalMap;
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (uriUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, uriUserName);
        if (uriUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, uriUserRealm);
        finalMap = builder.getMap();
        return doConnect(destination, finalMap, callbackHandler);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final String userName, final String realmName, final char[] password) throws IOException {
        final UserAndRealm userRealm = getUserAndRealm(destination);
        final String uriUserName = userRealm.getUser();
        final String uriUserRealm = userRealm.getRealm();
        final String actualUserName = userName != null ? userName : uriUserName != null ? uriUserName : connectOptions.get(RemotingOptions.AUTHORIZE_ID);
        final String actualUserRealm = realmName != null ? realmName : uriUserRealm != null ? uriUserRealm : connectOptions.get(RemotingOptions.AUTH_REALM);
        final OptionMap.Builder builder = OptionMap.builder().addAll(connectOptions);
        if (actualUserName != null) builder.set(RemotingOptions.AUTHORIZE_ID, actualUserName);
        if (actualUserRealm != null) builder.set(RemotingOptions.AUTH_REALM, actualUserRealm);
        final OptionMap finalMap = builder.getMap();
        return doConnect(destination, finalMap, new PasswordClientCallbackHandler(actualUserName, actualUserRealm, password));
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_CONNECTION_PROVIDER_PERM);
        }
        final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl();
        final ConnectionProvider provider = providerFactory.createInstance(context, optionMap);
        boolean ok = false;
        try {
            if (connectionProviders.putIfAbsent(uriScheme, provider) != null) {
                throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
            }
            log.tracef("Adding connection provider registration named '%s': %s", uriScheme, provider);
            final Registration registration = new MapRegistration<ConnectionProvider>(connectionProviders, uriScheme, provider) {
                protected void closeAction() {
                    super.closeAction();
                    provider.close();
                }
            };
            // automatically close the registration when the endpoint is closed
            final Key key = addCloseHandler(SpiUtils.closingCloseHandler(registration));
            registration.addCloseHandler(new CloseHandler<Registration>() {
                public void handleClose(final Registration closed) {
                    key.remove();
                }
            });
            ok = true;
            return registration;
        } finally {
            if (! ok) {
                provider.close();
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

    public String toString() {
        return "endpoint \"" + name + "\" <" + Integer.toHexString(hashCode()) + ">";
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
            super(executor, false);
            this.map = map;
            this.key = key;
            this.value = value;
        }

        protected void closeAction() {
            map.remove(key, value);
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

        public void openService(final Channel newChannel, final String serviceType) throws ServiceNotFoundException {
            final OpenListener listener = registeredServices.get(serviceType);
            if (listener == null) {
                throw new ServiceNotFoundException("Unable to find service type '" + serviceType + "'");
            }
            executor.execute(new Runnable() {
                public void run() {
                    listener.channelOpened(newChannel);
                }
            });
        }

        public void remoteClosed() {
            IoUtils.safeClose(connection);
        }
    }

    private final class ConnectionProviderContextImpl implements ConnectionProviderContext {

        private ConnectionProviderContextImpl() {
        }

        public Executor getExecutor() {
            return executor;
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory) {
            new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this, "client");
        }

        public Endpoint getEndpoint() {
            return EndpointImpl.this;
        }
    }
}

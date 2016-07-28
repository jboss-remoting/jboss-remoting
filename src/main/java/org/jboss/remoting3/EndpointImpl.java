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

import static java.security.AccessController.doPrivileged;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.security.sasl.SaslClientFactory;

import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.jboss.remoting3.remote.HttpUpgradeConnectionProviderFactory;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.RemotingPermission;
import org.jboss.remoting3.spi.AbstractHandleableCloseable;
import org.jboss.remoting3.spi.ConnectionHandlerContext;
import org.jboss.remoting3.spi.ConnectionHandlerFactory;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.RegisteredService;
import org.jboss.remoting3.spi.SpiUtils;

import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.sasl.util.PrivilegedSaslClientFactory;
import org.wildfly.security.sasl.util.ProtocolSaslClientFactory;
import org.wildfly.security.sasl.util.SaslFactories;
import org.wildfly.security.sasl.util.ServerNameSaslClientFactory;

import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Result;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 *
 */
final class EndpointImpl extends AbstractHandleableCloseable<Endpoint> implements Endpoint {

    static {
        // Print Remoting "greeting" message
        Logger.getLogger("org.jboss.remoting").infof("JBoss Remoting version %s", Version.getVersionString());
    }

    private static final Logger log = Logger.getLogger("org.jboss.remoting.endpoint");

    private static final int CLOSED_FLAG = 0x80000000;
    private static final int COUNT_MASK = ~CLOSED_FLAG;
    private static final String FQCN = EndpointImpl.class.getName();

    private final Set<ConnectionImpl> connections = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<ConnectionImpl, Boolean>()));

    private final Attachments attachments = new Attachments();

    private final ConcurrentMap<String, ConnectionProvider> connectionProviders = new UnlockedReadHashMap<String, ConnectionProvider>();
    private final ConcurrentMap<String, RegisteredServiceImpl> registeredServices = new UnlockedReadHashMap<String, RegisteredServiceImpl>();
    private final ConcurrentMap<ConnectionKey, FutureConnection> configuredConnections = new ConcurrentHashMap<>();

    private final Xnio xnio;
    private final XnioWorker worker;

    private final Object connectionLock = new Object();

    private static final AtomicIntegerFieldUpdater<EndpointImpl> resourceCountUpdater = AtomicIntegerFieldUpdater.newUpdater(EndpointImpl.class, "resourceCount");

    @SuppressWarnings("unused")
    private volatile int resourceCount = 0;

    private static final Pattern VALID_SERVICE_PATTERN = Pattern.compile("[-.:a-zA-Z_0-9]+");

    static final AuthenticationContextConfigurationClient AUTH_CONFIGURATION_CLIENT = doPrivileged(AuthenticationContextConfigurationClient.ACTION);

    /**
     * The name of this endpoint.
     */
    private final String name;
    private final ConnectionProviderContext connectionProviderContext;
    private final CloseHandler<Object> resourceCloseHandler = (closed, exception) -> closeTick1(closed);
    private final CloseHandler<Connection> connectionCloseHandler = (closed, exception) -> connections.remove(closed);
    private final boolean ourWorker;
    private final SocketAddress defaultBindAddress;

    private EndpointImpl(final XnioWorker xnioWorker, final boolean ourWorker, final String name, final SocketAddress defaultBindAddress) throws NotOpenException {
        super(xnioWorker, true);
        worker = xnioWorker;
        this.ourWorker = ourWorker;
        this.xnio = xnioWorker.getXnio();
        this.name = name;
        this.defaultBindAddress = defaultBindAddress;
        // initialize CPC
        connectionProviderContext = new ConnectionProviderContextImpl();
        // get XNIO worker
        log.tracef("Completed open of %s", this);
    }

    static EndpointImpl construct(final EndpointBuilder endpointBuilder) throws IOException {
        final String endpointName = endpointBuilder.getEndpointName();
        final List<ConnectionBuilder> connectionBuilders = endpointBuilder.getConnectionBuilders();
        final List<ConnectionProviderFactoryBuilder> factoryBuilders = endpointBuilder.getConnectionProviderFactoryBuilders();
        final EndpointImpl endpoint;
        XnioWorker xnioWorker = endpointBuilder.getXnioWorker();
        final SocketAddress defaultBindAddress = endpointBuilder.getDefaultBindAddress();
        if (xnioWorker == null) {
            Xnio xnio = Xnio.getInstance(EndpointImpl.class.getClassLoader());
            final OptionMap.Builder builder = OptionMap.builder();
            final OptionMap workerOptions = endpointBuilder.getXnioWorkerOptions();
            if (workerOptions != null) {
                builder.addAll(workerOptions);
            }
            builder.addAll(OptionMap.create(Options.THREAD_DAEMON, Boolean.TRUE));
            final OptionMap modifiedOptionMap = builder.set(Options.WORKER_NAME, endpointName == null ? "Remoting (anonymous)" : "Remoting \"" + endpointName + "\"").getMap();
            final AtomicReference<EndpointImpl> endpointRef = new AtomicReference<EndpointImpl>();
            xnioWorker = xnio.createWorker(null, modifiedOptionMap, () -> {
                final EndpointImpl e = endpointRef.getAndSet(null);
                if (e != null) {
                    e.closeComplete();
                }
            });
            endpointRef.set(endpoint = new EndpointImpl(xnioWorker, true, endpointName, defaultBindAddress));
        } else {
            endpoint = new EndpointImpl(xnioWorker, false, endpointName, defaultBindAddress);
        }
        boolean ok = false;
        try {
            if (factoryBuilders != null) for (ConnectionProviderFactoryBuilder factoryBuilder : factoryBuilders) {
                final String className = factoryBuilder.getClassName();
                final String moduleName = factoryBuilder.getModuleName();
                final ClassLoader classLoader;
                if (moduleName != null) {
                    // modules code here only
                    final Module module;
                    try {
                        module = Module.getCallerModuleLoader().loadModule(ModuleIdentifier.fromString(moduleName));
                    } catch (ModuleLoadException e) {
                        throw new IOException("Failed to create endpoint", e);
                    }
                    classLoader = module.getClassLoader();
                } else if (className == null) {
                    throw new IllegalArgumentException("Either class or module name required for connection provider factory");
                } else {
                    classLoader = EndpointImpl.class.getClassLoader();
                }
                if (className == null) {
                    final ServiceLoader<ConnectionProviderFactory> loader = ServiceLoader.load(ConnectionProviderFactory.class, classLoader);
                    for (ConnectionProviderFactory factory : loader) {
                        endpoint.addConnectionProvider(factoryBuilder.getScheme(), factory, OptionMap.EMPTY);
                    }
                } else try {
                    final Class<? extends ConnectionProviderFactory> factoryClass = classLoader.loadClass(className).asSubclass(ConnectionProviderFactory.class);
                    final ConnectionProviderFactory factory = factoryClass.newInstance();
                    endpoint.addConnectionProvider(factoryBuilder.getScheme(), factory, OptionMap.EMPTY);
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Unable to load connection provider factory class '" + className + "'", e);
                }
            }
            // remote (SSL is explicit in URL)
            final RemoteConnectionProviderFactory remoteConnectionProviderFactory = new RemoteConnectionProviderFactory();
            endpoint.addConnectionProvider("remote", remoteConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("remote+tls", remoteConnectionProviderFactory, OptionMap.create(Options.SECURE, Boolean.TRUE));
            // old (SSL is config-based)
            endpoint.addConnectionProvider("remoting", remoteConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE));
            // http - SSL is handled by the HTTP layer
            final HttpUpgradeConnectionProviderFactory httpUpgradeConnectionProviderFactory = new HttpUpgradeConnectionProviderFactory();
            endpoint.addConnectionProvider("remote+http", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("remote+https", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            // old
            endpoint.addConnectionProvider("http-remoting", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            endpoint.addConnectionProvider("https-remoting", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            final AuthenticationContext captured = AuthenticationContext.captureCurrent();
            if (connectionBuilders != null) for (ConnectionBuilder connectionBuilder : connectionBuilders) {
                SaslClientFactory saslClientFactory = connectionBuilder.getSaslClientFactory();
                if (saslClientFactory == null) {
                    saslClientFactory = SaslFactories.getElytronSaslClientFactory();
                }
                AuthenticationContext context = connectionBuilder.getAuthenticationContext();
                if (context == null) {
                    context = captured;
                }
                final URI uri = connectionBuilder.getUri();
                final boolean immediate = connectionBuilder.isImmediate();
                // todo: connect options from builder
                final FutureConnection futureConnection = new FutureConnection(endpoint, connectionBuilder.getBindAddress(), uri, immediate, OptionMap.EMPTY, context, saslClientFactory);
                if (endpoint.configuredConnections.putIfAbsent(new ConnectionKey(uri.getHost(), uri.getScheme(), uri.getUserInfo(), uri.getPort()), futureConnection) == null) {
                    // don't actually do this if there is a duplicate item configured
                    if (immediate) {
                        // initiate the connect attempt
                        endpoint.getConnection(uri);
                    }
                }
            }
            ok = true;
            return endpoint;
        } finally {
            if (! ok) endpoint.closeAsync();
        }
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

    protected void closeComplete() {
        super.closeComplete();
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
        if (ourWorker) {
            worker.shutdown();
        } else {
            closeComplete();
        }
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
            sm.checkPermission(RemotingPermission.REGISTER_SERVICE);
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
        registration.addCloseHandler((closed, exception) -> key.remove());
        return registration;
    }

    public IoFuture<Connection> getConnection(final URI destination) {
        Assert.checkNotNullParam("destination", destination);
        final String scheme = destination.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("No scheme given in URI '" + destination + "'");
        }
        final String host = destination.getHost();
        if (host == null) {
            throw new IllegalArgumentException("No host given in URI '" + destination + "'");
        }
        final String userInfo = destination.getUserInfo();
        final int port = destination.getPort();
        final ConnectionKey connectionKey = new ConnectionKey(host, scheme, userInfo, port);

        FutureConnection futureConnection = configuredConnections.get(connectionKey);
        if (futureConnection != null) {
            return futureConnection.get();
        }
        // it's not presently managed; we'll use defaults to set it up
        FutureConnection appearing;
        futureConnection = new FutureConnection(this, null, destination, false, OptionMap.EMPTY, RemotingXmlParser.getGlobalDefaultAuthCtxt(), SaslFactories.getElytronSaslClientFactory());
        if ((appearing = configuredConnections.putIfAbsent(connectionKey, futureConnection)) != null) {
            return appearing.get();
        }
        return futureConnection.get();
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        return connect(destination, connectOptions, AuthenticationContext.captureCurrent(), SaslFactories.getElytronSaslClientFactory());
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, SaslClientFactory saslClientFactory) throws IOException {
        return connect(destination, connectOptions, AuthenticationContext.captureCurrent(), saslClientFactory);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext) throws IOException {
        return connect(destination, connectOptions, authenticationContext, SaslFactories.getElytronSaslClientFactory());
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext, SaslClientFactory saslClientFactory) throws IOException {
        return connect(destination, null, connectOptions, authenticationContext, saslClientFactory);
    }

    public IoFuture<Connection> connect(final URI destination, final InetSocketAddress bindAddress, final OptionMap connectOptions, final AuthenticationContext authenticationContext, SaslClientFactory saslClientFactory) throws IOException {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("connectOptions", connectOptions);
        Assert.checkNotNullParam("saslClientFactory", saslClientFactory);
        saslClientFactory = new PrivilegedSaslClientFactory(saslClientFactory);
        final String protocol = connectOptions.contains(RemotingOptions.SASL_PROTOCOL) ? connectOptions.get(RemotingOptions.SASL_PROTOCOL) : RemotingOptions.DEFAULT_SASL_PROTOCOL;
        saslClientFactory = new ProtocolSaslClientFactory(saslClientFactory, protocol);
        if (connectOptions.contains(RemotingOptions.SERVER_NAME)) {
            saslClientFactory = new ServerNameSaslClientFactory(saslClientFactory, connectOptions.get(RemotingOptions.SERVER_NAME));
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.CONNECT);
        }
        final String scheme = destination.getScheme();
        synchronized (connectionLock) {
            boolean ok = false;
            resourceUntick("Connection to " + destination);
            try {
                final ConnectionProvider connectionProvider = connectionProviders.get(scheme);
                if (connectionProvider == null) {
                    throw new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed");
                }
                final FutureResult<Connection> futureResult = new FutureResult<Connection>(getExecutor());
                // Mark the stack because otherwise debugging connect problems can be incredibly tough
                final StackTraceElement[] mark = Thread.currentThread().getStackTrace();
                final Result<ConnectionHandlerFactory> result = new Result<ConnectionHandlerFactory>() {
                    private final AtomicBoolean flag = new AtomicBoolean();
                    public boolean setCancelled() {
                        if (! flag.compareAndSet(false, true)) {
                            return false;
                        }
                        log.logf(getClass().getName(), Logger.Level.TRACE, null, "Registered cancellation result");
                        closeTick1("a cancelled connection");
                        futureResult.setCancelled();
                        return true;
                    }

                    public boolean setException(final IOException exception) {
                        if (! flag.compareAndSet(false, true)) {
                            return false;
                        }
                        log.logf(getClass().getName(), Logger.Level.TRACE, exception, "Registered exception result");
                        closeTick1("a failed connection (2)");
                        SpiUtils.glueStackTraces(exception, mark, 1, "asynchronous invocation");
                        futureResult.setException(exception);
                        return true;
                    }

                    public boolean setResult(final ConnectionHandlerFactory connHandlerFactory) {
                        if (! flag.compareAndSet(false, true)) {
                            return false;
                        }
                        synchronized (connectionLock) {
                            log.logf(getClass().getName(), Logger.Level.TRACE, null, "Registered successful result %s", connHandlerFactory);
                            final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connHandlerFactory, connectionProviderContext, destination);
                            connections.add(connection);
                            connection.getConnectionHandler().addCloseHandler(SpiUtils.asyncClosingCloseHandler(connection));
                            connection.addCloseHandler(resourceCloseHandler);
                            connection.addCloseHandler(connectionCloseHandler);
                            // see if we were closed in the meantime
                            if (EndpointImpl.this.isCloseFlagSet()) {
                                connection.closeAsync();
                                futureResult.setCancelled();
                            } else {
                                futureResult.setResult(connection);
                            }
                        }
                        return true;
                    }
                };
                final AuthenticationConfiguration configuration = AUTH_CONFIGURATION_CLIENT.getAuthenticationConfiguration(destination, authenticationContext);
                final Cancellable connect = connectionProvider.connect(destination, bindAddress, connectOptions, result, configuration, saslClientFactory);
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

    public boolean isConnected(final URI uri) {
        if (uri == null) {
            return false;
        }
        final String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }
        final String host = uri.getHost();
        if (host == null) {
            return false;
        }
        final String userInfo = uri.getUserInfo();
        final int port = uri.getPort();
        final ConnectionKey connectionKey = new ConnectionKey(host, scheme, userInfo, port);

        FutureConnection futureConnection = configuredConnections.get(connectionKey);
        return futureConnection != null && futureConnection.isConnected();
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.ADD_CONNECTION_PROVIDER);
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
                provider.addCloseHandler((closed, exception) -> {
                    registration.closeAsync();
                    closeTick1(closed);
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
            sm.checkPermission(RemotingPermission.GET_CONNECTION_PROVIDER_INTERFACE);
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
                    final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this, null);
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

    final class TrackingExecutor implements Executor {
        private final AtomicInteger count = new AtomicInteger();

        public void execute(final Runnable command) {
            final AtomicInteger count = this.count;
            final int i = count.getAndIncrement();
            boolean ok = false;
            try {
                if (i == 0) {
                    executorUntick(this);
                }
                worker.execute(() -> {
                    try {
                        command.run();
                    } finally {
                        finishWork();
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

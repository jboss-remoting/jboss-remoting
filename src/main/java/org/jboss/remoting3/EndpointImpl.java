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
import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.PrivilegedAction;
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
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.net.ssl.SSLContext;
import javax.security.sasl.SaslClientFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3._private.Messages;
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
import org.wildfly.security.auth.AuthenticationException;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.AuthenticationContextConfigurationClient;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.sasl.util.PrivilegedSaslClientFactory;
import org.wildfly.security.sasl.util.ProtocolSaslClientFactory;
import org.wildfly.security.sasl.util.ServerNameSaslClientFactory;

import org.xnio.Bits;
import org.xnio.Cancellable;
import org.xnio.FailedIoFuture;
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

    private static final String[] NO_STRINGS = new String[0];

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

    private final ConcurrentMap<String, ProtocolRegistration> connectionProviders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RegisteredServiceImpl> registeredServices = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConnectionKey, ConnectionInfo> managedConnections = new ConcurrentHashMap<>();

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
    private final CloseHandler<Object> resourceCloseHandler = (closed, exception) -> closeTick1(closed);
    private final CloseHandler<Connection> connectionCloseHandler = (closed, exception) -> connections.remove(closed);
    private final boolean ourWorker;

    private final MBeanServer server;
    private final ObjectName objectName;

    private EndpointImpl(final XnioWorker xnioWorker, final boolean ourWorker, final String name) throws NotOpenException {
        super(xnioWorker, true);
        worker = xnioWorker;
        this.ourWorker = ourWorker;
        this.name = name;
        MBeanServer server = null;
        ObjectName objectName = null;
        try {
            server = ManagementFactory.getPlatformMBeanServer();
            String objName;
            if (name == null) {
                objName = "Remoting (anonymous)";
            } else {
                objName = "Remoting-" + name;
            }
            objectName = new ObjectName("jboss.remoting.endpoint", "name", objName + "-" + hashCode());
            server.registerMBean(new EndpointMXBean() {
                public String getEndpointName() {
                    return name;
                }

                public String[] getSupportedProtocolNames() {
                    return connectionProviders.keySet().toArray(NO_STRINGS);
                }

                public String[] getRegisteredServices() {
                    return registeredServices.keySet().toArray(NO_STRINGS);
                }

                public boolean getCloseRequested() {
                    return (resourceCount & CLOSED_FLAG) != 0;
                }

                public int getResourceCount() {
                    return resourceCount & COUNT_MASK;
                }

                public String[] getManagedConnectionURIs() {
                    final ConnectionKey[] connectionKeys = managedConnections.keySet().toArray(new ConnectionKey[0]);
                    final String[] result = new String[connectionKeys.length];
                    for (int i = 0, connectionKeysLength = connectionKeys.length; i < connectionKeysLength; i++) {
                        result[i] = connectionKeys[i].getRealUri().toString();
                    }
                    return result;
                }

                public int getConnectionCount() {
                    return connections.size();
                }
            }, objectName);
        } catch (Exception e) {
            // ignore
        }
        this.server = server;
        this.objectName = objectName;
        log.tracef("Completed open of %s", this);
    }

    static EndpointImpl construct(final EndpointBuilder endpointBuilder) throws IOException {
        final String endpointName = endpointBuilder.getEndpointName();
        final List<ConnectionProviderFactoryBuilder> factoryBuilders = endpointBuilder.getConnectionProviderFactoryBuilders();
        final EndpointImpl endpoint;
        XnioWorker xnioWorker = endpointBuilder.getXnioWorker();
        if (xnioWorker == null) {
            final XnioWorker.Builder workerBuilder = endpointBuilder.getWorkerBuilder();
            if (workerBuilder == null) {
                xnioWorker = XnioWorker.getContextManager().get();
                endpoint = new EndpointImpl(xnioWorker, false, endpointName);
            } else {
                final AtomicReference<EndpointImpl> endpointRef = new AtomicReference<EndpointImpl>();
                workerBuilder.setDaemon(true);
                workerBuilder.setWorkerName(endpointName == null ? "Remoting (anonymous)" : "Remoting \"" + endpointName + "\"");
                workerBuilder.setTerminationTask(() -> {
                    final EndpointImpl e = endpointRef.getAndSet(null);
                    if (e != null) {
                        e.closeComplete();
                    }
                });
                xnioWorker = workerBuilder.build();
                endpointRef.set(endpoint = new EndpointImpl(xnioWorker, true, endpointName));
            }
        } else {
            endpoint = new EndpointImpl(xnioWorker, false, endpointName);
        }
        boolean ok = false;
        try {
            if (factoryBuilders != null) for (ConnectionProviderFactoryBuilder factoryBuilder : factoryBuilders) {
                final String className = factoryBuilder.getClassName();
                final String moduleName = factoryBuilder.getModuleName();
                final ClassLoader classLoader;
                if (moduleName != null) {
                    classLoader = ModuleLoader.getClassLoaderFromModule(moduleName);
                } else if (className == null) {
                    throw new IllegalArgumentException("Either class or module name required for connection provider factory");
                } else {
                    classLoader = EndpointImpl.class.getClassLoader();
                }
                if (className == null) {
                    final ServiceLoader<ConnectionProviderFactory> loader = ServiceLoader.load(ConnectionProviderFactory.class, classLoader);
                    for (ConnectionProviderFactory factory : loader) {
                        endpoint.addConnectionProvider(factoryBuilder.getScheme(), factory, OptionMap.EMPTY);
                        for (String alias : factoryBuilder.getAliases()) {
                            endpoint.addConnectionProvider(alias, factory, OptionMap.EMPTY);
                        }
                    }
                } else try {
                    final Class<? extends ConnectionProviderFactory> factoryClass = classLoader.loadClass(className).asSubclass(ConnectionProviderFactory.class);
                    final ConnectionProviderFactory factory = factoryClass.newInstance();
                    endpoint.addConnectionProvider(factoryBuilder.getScheme(), factory, OptionMap.EMPTY);
                    for (String alias : factoryBuilder.getAliases()) {
                        endpoint.addConnectionProvider(alias, factory, OptionMap.EMPTY);
                    }
                } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                    throw new IllegalArgumentException("Unable to load connection provider factory class '" + className + "'", e);
                }
            }
            // remote (SSL is explicit in URL)
            final RemoteConnectionProviderFactory remoteConnectionProviderFactory = new RemoteConnectionProviderFactory();
            endpoint.addConnectionProvider("remote", remoteConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE, Options.SSL_STARTTLS, Boolean.TRUE));
            endpoint.addConnectionProvider("remote+tls", remoteConnectionProviderFactory, OptionMap.create(Options.SECURE, Boolean.TRUE));
            // old (SSL is config-based)
            endpoint.addConnectionProvider("remoting", remoteConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.TRUE, Options.SSL_STARTTLS, Boolean.TRUE));
            // http - SSL is handled by the HTTP layer
            final HttpUpgradeConnectionProviderFactory httpUpgradeConnectionProviderFactory = new HttpUpgradeConnectionProviderFactory();
            endpoint.addConnectionProvider("remote+http", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE, Options.SSL_STARTTLS, Boolean.TRUE));
            endpoint.addConnectionProvider("remote+https", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SECURE, Boolean.TRUE));
            // old
            endpoint.addConnectionProvider("http-remoting", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE, Options.SSL_STARTTLS, Boolean.TRUE));
            endpoint.addConnectionProvider("https-remoting", httpUpgradeConnectionProviderFactory, OptionMap.create(Options.SECURE, Boolean.TRUE));
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
        try {
            super.closeComplete();
        } finally {
            if (server != null && objectName != null) {
                try {
                    server.unregisterMBean(objectName);
                } catch (Throwable ignored) {
                }
            }
        }
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
                for (ProtocolRegistration protocolRegistration : connectionProviders.values()) {
                    protocolRegistration.getProvider().closeAsync();
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

    public IoFuture<ConnectionPeerIdentity> getConnectedIdentity(final URI destination, final SSLContext sslContext, final AuthenticationConfiguration authenticationConfiguration) {
        return doGetConnection(destination, sslContext, authenticationConfiguration, true);
    }

    public IoFuture<ConnectionPeerIdentity> getConnectedIdentityIfExists(final URI destination, final SSLContext sslContext, final AuthenticationConfiguration authenticationConfiguration) {
        return doGetConnection(destination, sslContext, authenticationConfiguration, false);
    }

    IoFuture<ConnectionPeerIdentity> doGetConnection(final URI destination, final SSLContext sslContext, final AuthenticationConfiguration authenticationConfiguration, final boolean connect) {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("authenticationConfiguration", authenticationConfiguration);

        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;

        /*
         * Note: not obvious!  we *always* use the host/port from the connect configuration even if connection sharing isn't supported.
         * This is the only way we can be certain that the behavior experience is similar for the end user.
         */
        final String realHost = client.getRealHost(destination, authenticationConfiguration);
        if (realHost == null) {
            throw new IllegalArgumentException("No host given in URI '" + destination + "'");
        }
        final int realPort = client.getRealPort(destination, authenticationConfiguration);
        if (realPort == -1) {
            throw new IllegalArgumentException("No port number given in URI '" + destination + "'");
        }
        final String scheme = client.getRealProtocol(destination, authenticationConfiguration);
        if (scheme == null) {
            throw new IllegalArgumentException("No scheme given in URI '" + destination + "'");
        }

        // "sanitize" the destination URI
        final URI realDestination;
        try {
            realDestination = new URI(
                scheme,
                null,
                realHost,
                realPort,
                null,
                null,
                null
            );
        } catch (URISyntaxException e) {
            return new FailedIoFuture<>(new IOException(e));
        }

        final ConnectionKey connectionKey = new ConnectionKey(realDestination, sslContext);
        ConnectionInfo newConnectionInfo = managedConnections.get(connectionKey);
        while (newConnectionInfo == null) {
            final ConnectionInfo appearing = managedConnections.putIfAbsent(connectionKey, newConnectionInfo = new ConnectionInfo(OptionMap.EMPTY));
            if (appearing != null) {
                newConnectionInfo = appearing;
            }
        }
        final IoFuture<Connection> futureConnection = newConnectionInfo.getConnection(this, connectionKey, authenticationConfiguration, connect);
        if (futureConnection == null) {
            // no connection currently exists
            return null;
        }
        final FutureResult<ConnectionPeerIdentity> futureResult = new FutureResult<>(getExecutor());
        futureResult.addCancelHandler(futureConnection);
        futureConnection.addNotifier(new IoFuture.HandlingNotifier<Connection, FutureResult<ConnectionPeerIdentity>>() {
            public void handleCancelled(final FutureResult<ConnectionPeerIdentity> attachment) {
                futureResult.setCancelled();
            }

            public void handleFailed(final IOException exception, final FutureResult<ConnectionPeerIdentity> attachment) {
                futureResult.setException(exception);
            }

            public void handleDone(final Connection connection, final FutureResult<ConnectionPeerIdentity> attachment) {
                try {
                    final ConnectionPeerIdentity identity = connection.getPeerIdentityContext().authenticate(authenticationConfiguration);
                    futureResult.setResult(identity);
                } catch (AuthenticationException e) {
                    futureResult.setException(e);
                }
            }
        }, futureResult);
        return futureResult.getIoFuture();
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) {
        return connect(destination, connectOptions, AuthenticationContext.captureCurrent());
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext) {
        return connect(destination, null, connectOptions, authenticationContext);
    }

    public IoFuture<Connection> connect(final URI destination, final InetSocketAddress bindAddress, final OptionMap connectOptions, final AuthenticationContext authenticationContext) {
        final AuthenticationContextConfigurationClient client = AUTH_CONFIGURATION_CLIENT;
        final AuthenticationConfiguration configuration = client.getAuthenticationConfiguration(destination, authenticationContext, - 1, null, null);
        final SSLContext sslContext;
        try {
            sslContext = client.getSSLContext(destination, authenticationContext, null, null);
        } catch (GeneralSecurityException e) {
            return new FailedIoFuture<>(Messages.conn.failedToConfigureSslContext(e));
        }
        return connect(destination, bindAddress, connectOptions, configuration, sslContext);
    }

    public IoFuture<Connection> connect(final URI destination, final InetSocketAddress bindAddress, final OptionMap connectOptions, final SSLContext sslContext, final AuthenticationConfiguration connectionConfiguration) {
        return connect(destination, bindAddress, connectOptions, connectionConfiguration, sslContext);
    }

    IoFuture<Connection> connect(final URI destination, final SocketAddress bindAddress, final OptionMap connectOptions, final AuthenticationConfiguration configuration, final SSLContext sslContext) {
        Assert.checkNotNullParam("destination", destination);
        Assert.checkNotNullParam("connectOptions", connectOptions);
        final String protocol = connectOptions.contains(RemotingOptions.SASL_PROTOCOL) ? connectOptions.get(RemotingOptions.SASL_PROTOCOL) : RemotingOptions.DEFAULT_SASL_PROTOCOL;
        UnaryOperator<SaslClientFactory> factoryOperator = factory -> new ProtocolSaslClientFactory(factory, protocol);
        if (connectOptions.contains(RemotingOptions.SERVER_NAME)) {
            final String serverName = connectOptions.get(RemotingOptions.SERVER_NAME);
            factoryOperator = and(factoryOperator, factory -> new ServerNameSaslClientFactory(factory, serverName));
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.CONNECT);
        }
        final String scheme = AUTH_CONFIGURATION_CLIENT.getRealProtocol(destination, configuration);
        synchronized (connectionLock) {
            boolean ok = false;
            try {
                resourceUntick("Connection to " + destination);
            } catch (NotOpenException e) {
                return new FailedIoFuture<>(e);
            }
            try {
                final ProtocolRegistration protocolRegistration = connectionProviders.get(scheme);
                if (protocolRegistration == null) {
                    return new FailedIoFuture<>(new UnknownURISchemeException("No connection provider for URI scheme \"" + scheme + "\" is installed"));
                }
                final ConnectionProvider connectionProvider = protocolRegistration.getProvider();
                final FutureResult<Connection> futureResult = new FutureResult<Connection>(getExecutor());
                // Mark the stack because otherwise debugging connect problems can be incredibly tough
                final StackTraceElement[] mark = Thread.currentThread().getStackTrace();
                final UnaryOperator<SaslClientFactory> finalFactoryOperator = factoryOperator;
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
                            final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connHandlerFactory, protocolRegistration.getContext(), destination, null, configuration, protocol);
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
                final Cancellable connect = doPrivileged((PrivilegedAction<Cancellable>) () ->
                        connectionProvider.connect(destination, bindAddress, connectOptions, result, configuration, sslContext, finalFactoryOperator, Collections.emptyList())
                );
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

    private static <T> UnaryOperator<T> and(final UnaryOperator<T> first, final UnaryOperator<T> second) {
        return t -> second.apply(first.apply(t));
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(RemotingPermission.ADD_CONNECTION_PROVIDER);
        }
        boolean ok = false;
        resourceUntick("Connection provider for " + uriScheme);
        try {
            final String saslProtocol = optionMap.contains(RemotingOptions.SASL_PROTOCOL) ? optionMap.get(RemotingOptions.SASL_PROTOCOL) : RemotingOptions.DEFAULT_SASL_PROTOCOL;
            final ConnectionProviderContextImpl context = new ConnectionProviderContextImpl(uriScheme, saslProtocol);
            final ConnectionProvider provider = providerFactory.createInstance(context, optionMap, uriScheme);
            final ProtocolRegistration protocolRegistration = new ProtocolRegistration(provider, context);
            try {
                if (connectionProviders.putIfAbsent(uriScheme, protocolRegistration) != null) {
                    safeClose(provider);
                    throw new DuplicateRegistrationException("URI scheme '" + uriScheme + "' is already registered to a provider");
                }
                // add a resource count for close
                log.tracef("Adding connection provider registration named '%s': %s", uriScheme, provider);
                final Registration registration = new MapRegistration<ProtocolRegistration>(connectionProviders, uriScheme, protocolRegistration) {
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

    static final class ProtocolRegistration {
        private final ConnectionProvider provider;
        private final ConnectionProviderContextImpl context;

        ProtocolRegistration(final ConnectionProvider provider, final ConnectionProviderContextImpl context) {
            this.provider = provider;
            this.context = context;
        }

        ConnectionProvider getProvider() {
            return provider;
        }

        ConnectionProviderContextImpl getContext() {
            return context;
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
        final ProtocolRegistration protocolRegistration = connectionProviders.get(uriScheme);
        if (protocolRegistration == null) {
            throw new UnknownURISchemeException("No connection provider for URI scheme \"" + uriScheme + "\" is installed");
        }
        return expectedType.cast(protocolRegistration.getProvider().getProviderInterface());
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

    class MapRegistration<T> extends AbstractHandleableCloseable<Registration> implements Registration {

        private final ConcurrentMap<String, T> map;
        private final String key;
        private final T value;

        MapRegistration(final ConcurrentMap<String, T> map, final String key, final T value) {
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

        T getValue() {
            return value;
        }

        public String toString() {
            return String.format("Registration of '%s': %s", key, value);
        }
    }

    final class LocalConnectionContext implements ConnectionHandlerContext {
        private final ConnectionProviderContext connectionProviderContext;
        private final ConnectionImpl connection;

        LocalConnectionContext(final ConnectionProviderContext connectionProviderContext, final ConnectionImpl connection) {
            this.connectionProviderContext = connectionProviderContext;
            this.connection = connection;
        }

        public ConnectionProviderContext getConnectionProviderContext() {
            return connectionProviderContext;
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

        // client -> server

        public void receiveAuthRequest(final int id, final String mechName, final byte[] initialResponse) {
            connection.receiveAuthRequest(id, mechName, initialResponse);
        }

        public void receiveAuthResponse(final int id, final byte[] response) {
            connection.receiveAuthResponse(id, response);
        }

        public void receiveAuthDelete(final int id) {
            connection.receiveAuthDelete(id);
        }

        // server -> client

        public void receiveAuthChallenge(final int id, final byte[] challenge) {
            if (id == 0 || id == 1) {
                // ignore
                return;
            }
            connection.getPeerIdentityContext().receiveChallenge(id, challenge);
        }

        public void receiveAuthSuccess(final int id, final byte[] challenge) {
            if (id == 0 || id == 1) {
                // ignore
                return;
            }
            connection.getPeerIdentityContext().receiveSuccess(id, challenge);
        }

        public void receiveAuthReject(final int id) {
            if (id == 0 || id == 1) {
                // ignore
                return;
            }
            connection.getPeerIdentityContext().receiveReject(id);
        }

        public void receiveAuthDeleteAck(final int id) {
            if (id == 0 || id == 1) {
                // ignore
                return;
            }
            connection.getPeerIdentityContext().receiveDeleteAck(id);
        }
    }

    final class ConnectionProviderContextImpl implements ConnectionProviderContext {

        private final String protocol;
        private final String saslProtocol;

        ConnectionProviderContextImpl(final String protocol, String saslProtocol) {
            this.protocol = protocol;
            this.saslProtocol = saslProtocol;
        }

        public void accept(final ConnectionHandlerFactory connectionHandlerFactory, final SaslAuthenticationFactory authenticationFactory) {
            synchronized (connectionLock) {
                try {
                    resourceUntick("an inbound connection");
                } catch (NotOpenException e) {
                    throw new IllegalStateException("Accept after endpoint close", e);
                }
                boolean ok = false;
                try {
                    // XXX: we need to know if we in fact authenticated to the client via SSL, in which case we actually have an X500Principal
                    final ConnectionImpl connection = new ConnectionImpl(EndpointImpl.this, connectionHandlerFactory, this, null, authenticationFactory, AuthenticationConfiguration.EMPTY, saslProtocol);
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
            return worker.getXnio();
        }

        public Executor getExecutor() {
            return EndpointImpl.this.getExecutor();
        }

        public XnioWorker getXnioWorker() {
            return worker;
        }

        public String getProtocol() {
            return protocol;
        }
    }

    static class RegisteredServiceImpl implements RegisteredService {
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

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2026 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3.test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.PrivilegedAction;
import java.security.Security;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import javax.net.ssl.SSLContext;
import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;

import org.jboss.logging.Logger;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.security.WildFlyElytronProvider;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.client.MatchRule;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.MechanismConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.password.PasswordFactory;
import org.wildfly.security.password.spec.ClearPasswordSpec;
import org.wildfly.security.permission.PermissionVerifier;
import org.wildfly.security.sasl.SaslMechanismSelector;
import org.wildfly.security.sasl.util.AbstractDelegatingSaslServerFactory;
import org.wildfly.security.sasl.util.SaslMechanismInformation;
import org.wildfly.security.sasl.util.ServiceLoaderSaslServerFactory;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * Verifies that the server does not let a {@link java.util.concurrent.RejectedExecutionException} escape from the
 * connection open listener when the XNIO worker is shut down while a client connection is being authenticated
 * (e.g. the server is stopped while {@code jboss-cli.sh} or a remote JMX client is connecting).
 * <p>
 * The race is reproduced deterministically: the server's SASL server factory shuts down the server XNIO worker
 * from the IO thread while the initial authentication request is being processed, which is right before the
 * connection open listener suspends reads and submits the authentication step to the executor.
 *
 * @author <a href="mailto:rarosa@redhat.com">Rafael Rosa</a>
 */
public class ServerShutdownDuringAuthenticationTestCase {

    private static final int PORT = 30125;
    private static final String LISTENER_LOGGER = "org.xnio.listener";

    private static String providerName;

    private final List<LogRecord> listenerErrors = new CopyOnWriteArrayList<>();
    private final Handler listenerErrorHandler = new Handler() {
        @Override
        public void publish(final LogRecord record) {
            if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                listenerErrors.add(record);
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    // keep a strong reference so the handler is not lost if the logger is garbage collected
    private java.util.logging.Logger listenerLogger;

    private XnioWorker serverWorker;
    private Endpoint serverEndpoint;
    private Endpoint clientEndpoint;
    private Closeable server;

    @BeforeClass
    public static void doBeforeClass() {
        final WildFlyElytronProvider provider = new WildFlyElytronProvider();
        Security.addProvider(provider);
        providerName = provider.getName();
    }

    @AfterClass
    public static void doAfterClass() {
        Security.removeProvider(providerName);
    }

    @Before
    public void before() throws Exception {
        listenerLogger = java.util.logging.Logger.getLogger(LISTENER_LOGGER);
        listenerLogger.addHandler(listenerErrorHandler);

        serverWorker = Xnio.getInstance().createWorkerBuilder()
                .setWorkerName("server-shutdown-test")
                .setWorkerIoThreads(1)
                .build();
        serverEndpoint = Endpoint.builder().setEndpointName("server-shutdown-test-server").setXnioWorker(serverWorker).build();
        clientEndpoint = Endpoint.builder().setEndpointName("server-shutdown-test-client").build();
    }

    @After
    public void after() throws Exception {
        listenerLogger.removeHandler(listenerErrorHandler);
        IoUtils.safeClose(clientEndpoint);
        if (!serverWorker.isTerminated()) {
            // the test failed before the server worker was shut down, so release the server side resources normally
            IoUtils.safeClose(server);
            IoUtils.safeClose(serverEndpoint);
            serverWorker.shutdownNow();
        }
        // otherwise there is nothing left to release: the server IO thread closed its channels when it terminated,
        // and closing the server or the server endpoint now would wait forever for that IO thread
    }

    @Test(timeout = 60_000)
    public void testServerShutdownDuringAuthentication() throws Exception {
        final AtomicBoolean shutdownTriggered = new AtomicBoolean();
        server = createServer(shutdownTriggered);

        final IoFuture<Connection> futureConnection = connect();

        // wait for the server IO thread to finish handling the connection and terminate
        assertTrue("Server worker did not terminate", serverWorker.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue("Server worker shutdown was not triggered during authentication", shutdownTriggered.get());

        // the client must not be left waiting for an authentication outcome that will never come
        assertNotEquals("Client connection is still pending", IoFuture.Status.WAITING, futureConnection.await(10, TimeUnit.SECONDS));
        if (futureConnection.getStatus() == IoFuture.Status.DONE) {
            IoUtils.safeClose(futureConnection.get());
        }

        assertTrue("Unexpected error logged by a channel listener: " + describe(listenerErrors), listenerErrors.isEmpty());
    }

    private Closeable createServer(final AtomicBoolean shutdownTriggered) throws Exception {
        final NetworkServerProvider networkServerProvider = serverEndpoint.getConnectionProviderInterface("remote", NetworkServerProvider.class);
        final SecurityDomain.Builder domainBuilder = SecurityDomain.builder();
        final SimpleMapBackedSecurityRealm mainRealm = new SimpleMapBackedSecurityRealm();
        domainBuilder.addRealm("mainRealm", mainRealm).build();
        domainBuilder.setDefaultRealmName("mainRealm");
        domainBuilder.setPermissionMapper((permissionMappable, roles) -> PermissionVerifier.ALL);
        final PasswordFactory passwordFactory = PasswordFactory.getInstance("clear");
        mainRealm.setPasswordMap("bob", passwordFactory.generatePassword(new ClearPasswordSpec("pass".toCharArray())));

        // shut down the server worker from the IO thread while the authentication request is being processed
        final SaslServerFactory saslServerFactory = new AbstractDelegatingSaslServerFactory(new ServiceLoaderSaslServerFactory(getClass().getClassLoader())) {
            @Override
            public SaslServer createSaslServer(final String mechanism, final String protocol, final String serverName, final Map<String, ?> props, final CallbackHandler cbh) throws SaslException {
                final SaslServer saslServer = super.createSaslServer(mechanism, protocol, serverName, props, cbh);
                if (saslServer != null && shutdownTriggered.compareAndSet(false, true)) {
                    Logger.getLogger("TEST").info("Shutting down the server worker during authentication");
                    serverWorker.shutdown();
                }
                return saslServer;
            }
        };

        final SaslAuthenticationFactory.Builder builder = SaslAuthenticationFactory.builder();
        builder.setSecurityDomain(domainBuilder.build());
        builder.setFactory(saslServerFactory);
        builder.setMechanismConfigurationSelector(mechanismInformation -> SaslMechanismInformation.Names.SCRAM_SHA_256.equals(mechanismInformation.getMechanismName()) ? MechanismConfiguration.EMPTY : null);
        return networkServerProvider.createServer(new InetSocketAddress("localhost", PORT), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE), builder.build(), SSLContext.getDefault());
    }

    private IoFuture<Connection> connect() {
        final AuthenticationContext authenticationContext = AuthenticationContext.empty().with(MatchRule.ALL,
                AuthenticationConfiguration.empty().useName("bob").usePassword("pass").setSaslMechanismSelector(SaslMechanismSelector.NONE.addMechanism(SaslMechanismInformation.Names.SCRAM_SHA_256)));
        return authenticationContext.run((PrivilegedAction<IoFuture<Connection>>) () -> {
            try {
                return clientEndpoint.connect(new URI("remote://localhost:" + PORT), OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static String describe(final List<LogRecord> records) {
        final StringBuilder b = new StringBuilder();
        for (LogRecord record : records) {
            b.append("\n  ").append(record.getLevel()).append(' ').append(record.getLoggerName()).append(": ").append(record.getMessage());
            if (record.getThrown() != null) {
                b.append(" - ").append(record.getThrown());
            }
        }
        return b.toString();
    }
}

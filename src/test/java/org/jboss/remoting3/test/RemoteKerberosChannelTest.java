/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3.test;

import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.ldif.LdifEntry;
import org.apache.directory.api.ldap.model.ldif.LdifReader;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.server.annotations.CreateKdcServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.ContextEntry;
import org.apache.directory.server.core.annotations.CreateDS;
import org.apache.directory.server.core.annotations.CreateIndex;
import org.apache.directory.server.core.annotations.CreatePartition;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.factory.DSAnnotationProcessor;
import org.apache.directory.server.core.kerberos.KeyDerivationInterceptor;
import org.apache.directory.server.factory.ServerAnnotationProcessor;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.jboss.remoting3.Channel;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.OpenListener;
import org.jboss.remoting3.Registration;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.RemotingOptions;
import org.jboss.remoting3.remote.RemoteConnectionProviderFactory;
import org.jboss.remoting3.security.SimpleServerAuthenticationProvider;
import org.jboss.remoting3.spi.NetworkServerProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.OptionMap.Builder;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.channels.AcceptingChannel;

/**
 * Test for remote channel communication with Kerberos enabled.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RemoteKerberosChannelTest extends ChannelTestBase {

    private static final boolean IS_IBM = System.getProperty("java.vendor").contains("IBM");

    private static DirectoryService directoryService;
    private static KdcServer kdcServer;
    private static String originalConfig;

    protected static Endpoint endpoint;
    private static AcceptingChannel streamServer;
    private static Registration registration;
    private Connection connection;
    private Registration serviceRegistration;

    @BeforeClass
    public static void create() throws Exception {
        directoryService = createDirectoryService();
        kdcServer = createKDCServer();

        endpoint = Remoting.createEndpoint("test", OptionMap.EMPTY);
        registration = endpoint.addConnectionProvider("remote", new RemoteConnectionProviderFactory(),
                OptionMap.create(Options.SSL_ENABLED, Boolean.FALSE));
        final NetworkServerProvider networkServerProvider = endpoint.getConnectionProviderInterface("remote",
                NetworkServerProvider.class);

        Subject serverSubject = login("remoting/test_server", "servicepwd".toCharArray());
        final SimpleServerAuthenticationProvider sap = new SimpleServerAuthenticationProvider();
        streamServer = Subject.doAs(serverSubject, new PrivilegedExceptionAction<AcceptingChannel>() {
            @Override
            public AcceptingChannel run() throws Exception {
                Builder builder = OptionMap.builder();
                builder.set(Options.SASL_MECHANISMS, Sequence.of("GSSAPI"));
                builder.set(RemotingOptions.SERVER_NAME, "test_server");
                builder.set(RemotingOptions.SASL_PROTOCOL, "remoting");

                return networkServerProvider.createServer(new InetSocketAddress("localhost", 30123), builder.getMap(), sap, null);
            }
        });
    }

    @CreateDS(
            name = "JBossDS",
            partitions =
            {
                @CreatePartition(
                    name = "jboss",
                    suffix = "dc=jboss,dc=org",
                    contextEntry = @ContextEntry(
                        entryLdif =
                            "dn: dc=jboss,dc=org\n" +
                            "dc: jboss\n" +
                            "objectClass: top\n" +
                            "objectClass: domain\n\n" ),
                    indexes =
                    {
                        @CreateIndex( attribute = "objectClass" ),
                        @CreateIndex( attribute = "dc" ),
                        @CreateIndex( attribute = "ou" )
                    })
            },
            additionalInterceptors = { KeyDerivationInterceptor.class })
    public static DirectoryService createDirectoryService() throws Exception {
        DirectoryService directoryService = DSAnnotationProcessor.getDirectoryService();

        final InputStream ldifStream = RemoteKerberosChannelTest.class.getResourceAsStream("/KerberosTesting.ldif");

        SchemaManager schemaManager = directoryService.getSchemaManager();

        for (LdifEntry ldifEntry : new LdifReader(ldifStream)) {
            directoryService.getAdminSession().add(new DefaultEntry(schemaManager, ldifEntry.getEntry()));
        }

        return directoryService;
    }

    @CreateKdcServer(primaryRealm = "JBOSS.ORG",
            kdcPrincipal = "krbtgt/JBOSS.ORG@JBOSS.ORG",
            searchBaseDn = "dc=jboss,dc=org",
            transports =
            {
                @CreateTransport(protocol = "UDP", port = 6088)
            })
    public static KdcServer createKDCServer() throws Exception {
        final URL configPath = RemoteKerberosChannelTest.class.getResource("/krb5.conf");
        originalConfig = System.setProperty("java.security.krb5.conf", configPath.getFile());
        KdcServer kdcServer = KDCServerAnnotationProcessor.getKdcServer(directoryService, 1024, "localhost");
        // kdcServer.getConfig().setPaEncTimestampRequired(false); - This line will need to be enabled when we can use the
        // ServerAnnotationProcessor.
        return kdcServer;
    }

    static Subject login(final String userName, final char[] password) throws LoginException {
        Subject theSubject = new Subject();
        CallbackHandler cbh = new UsernamePasswordCBH(userName, password);
        LoginContext lc = new LoginContext("KDC", theSubject, cbh, createJaasConfiguration());
        lc.login();

        return theSubject;
    }

    private static Configuration createJaasConfiguration() {
        return new Configuration() {

            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                if ("KDC".equals(name) == false) {
                    throw new IllegalArgumentException("Unexpected name '" + name + "'");
                }

                AppConfigurationEntry[] entries = new AppConfigurationEntry[1];
                Map<String, Object> options = new HashMap<String, Object>();
                options.put("debug", "true");
                options.put("refreshKrb5Config", "true");

                if (IS_IBM) {
                    options.put("noAddress", "true");
                    options.put("credsType", "both");
                    entries[0] = new AppConfigurationEntry("com.ibm.security.auth.module.Krb5LoginModule", REQUIRED, options);
                } else {
                    options.put("storeKey", "true");
                    options.put("isInitiator", "true");
                    entries[0] = new AppConfigurationEntry("com.sun.security.auth.module.Krb5LoginModule", REQUIRED, options);
                }

                return entries;
            }

        };
    }

    @AfterClass
    public static void destroy() throws Exception {
        IoUtils.safeClose(streamServer);
        IoUtils.safeClose(endpoint);
        IoUtils.safeClose(registration);
        if (kdcServer != null) {
            kdcServer.stop();
        }
        if (directoryService != null) {
            directoryService.shutdown();
        }
        if (originalConfig != null) {
            System.setProperty("java.security.krb5.conf", originalConfig);
        }
    }

    @Before
    public void testStart() throws Exception {
        Subject clientSubject = login("jduke", "theduke".toCharArray());
        final FutureResult<Channel> passer = new FutureResult<Channel>();
        serviceRegistration = endpoint.registerService("org.jboss.test", new OpenListener() {
            public void channelOpened(final Channel channel) {
                passer.setResult(channel);
            }

            public void registrationTerminated() {
            }
        }, OptionMap.EMPTY);
        IoFuture<Connection> futureConnection = Subject.doAs(clientSubject,
                new PrivilegedExceptionAction<IoFuture<Connection>>() {

                    @Override
                    public IoFuture<Connection> run() throws Exception {
                        return endpoint.connect(new URI("remote://localhost:30123"), OptionMap.create(RemotingOptions.SASL_PROTOCOL, "remoting"));
                    }
                });
        connection = futureConnection.get();
        IoFuture<Channel> futureChannel = connection.openChannel("org.jboss.test", OptionMap.EMPTY);
        sendChannel = futureChannel.get();
        recvChannel = passer.getIoFuture().get();
        assertNotNull(recvChannel);
        assertEquals("jduke@JBOSS.ORG", recvChannel.getConnection().getUserInfo().getUserName());
    }

    @After
    public void testFinish() {
        IoUtils.safeClose(sendChannel);
        IoUtils.safeClose(recvChannel);
        IoUtils.safeClose(connection);
        serviceRegistration.close();
    }

    private static class UsernamePasswordCBH implements CallbackHandler {

        /*
         * Note: We use CallbackHandler implementations like this in test cases as test cases need to run unattended, a true
         * CallbackHandler implementation should interact directly with the current user to prompt for the username and
         * password.
         *
         * i.e. In a client app NEVER prompt for these values in advance and provide them to a CallbackHandler like this.
         */

        private final String username;
        private final char[] password;

        private UsernamePasswordCBH(final String username, final char[] password) {
            this.username = username;
            this.password = password;
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback current : callbacks) {
                if (current instanceof NameCallback) {
                    NameCallback ncb = (NameCallback) current;
                    ncb.setName(username);
                } else if (current instanceof PasswordCallback) {
                    PasswordCallback pcb = (PasswordCallback) current;
                    pcb.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(current);
                }
            }

        }

    }

}

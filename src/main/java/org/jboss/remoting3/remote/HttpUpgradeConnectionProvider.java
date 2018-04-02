/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ExternalConnectionProvider;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FailedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.channels.SslChannel;
import org.xnio.conduits.ConduitStreamSourceChannel;
import org.xnio.http.HandshakeChecker;
import org.xnio.http.HttpUpgrade;
import org.xnio.ssl.SslConnection;

/**
 *
 * Connection provider that performs a HTTP upgrade. The upgrade handshake borrows heavily from
 * the web socket protocol, but with the following changes:
 *
 * - The magic number used is CF70DEB8-70F9-4FBA-8B4F-DFC3E723B4CD instead of 258EAFA5-E914-47DA-95CA-C5AB0DC85B11
 * - The 'Sec-JbossRemoting-Key' header is used in place of the 'Sec-WebSocket-Key' challenge header
 * - The 'Sec-JbossRemoting-Accept' header is used in place of the 'Sec-WebSocket-Accept' header
 *
 * Other than that the handshake process is identical. Once the upgrade is completed the remoting handshake takes
 * place as normal.
 *
 * <p>
 * See also: <a href="http://tools.ietf.org/html/rfc6455">RFC 6455</a>
 *
 * @author Stuart Douglas
 */
final class HttpUpgradeConnectionProvider extends RemoteConnectionProvider {

    /**
     * Magic number used in the handshake.
     */
    public static final String MAGIC_NUMBER = "CF70DEB8-70F9-4FBA-8B4F-DFC3E723B4CD";

    //headers
    public static final String SEC_JBOSS_REMOTING_KEY = "Sec-JbossRemoting-Key";
    public static final String SEC_JBOSS_REMOTING_ACCEPT= "sec-jbossremoting-accept";
    public static final String UPGRADE = "Upgrade";

    private final ProviderInterface providerInterface = new ProviderInterface();

    HttpUpgradeConnectionProvider(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext) throws IOException {
        super(optionMap, connectionProviderContext);
    }

    protected IoFuture<StreamConnection> createConnection(final URI uri, final InetSocketAddress destination, final OptionMap connectOptions, final ChannelListener<StreamConnection> openListener) {
        final URI newUri;
        try {
            newUri = new URI("http", "", uri.getHost(), uri.getPort(), "/", "", "");
        } catch (URISyntaxException e) {
            return new FailedIoFuture<>(new IOException(e));
        }
        final Map<String, String> headers = new HashMap<String, String>();
        headers.put(UPGRADE, "jboss-remoting");
        final String secKey = createSecKey();
        headers.put(SEC_JBOSS_REMOTING_KEY, secKey);

        final FutureResult<StreamConnection> future = new FutureResult<>(getExecutor());

        HttpUpgrade.performUpgrade(getXnioWorker(), null, newUri, headers, channel -> {
            ChannelListeners.invokeChannelListener(channel, openListener);
        }, null, connectOptions, new RemotingHandshakeChecker(secKey))
                .addNotifier((ioFuture, attachment) -> {
                    if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                        future.setException(ioFuture.getException());
                    }
                }, null);

        return future.getIoFuture();
    }

    protected IoFuture<SslConnection> createSslConnection(final URI uri, final InetSocketAddress destination, final OptionMap options, final AuthenticationContext authenticationContext, final ChannelListener<StreamConnection> openListener) {
        final URI newUri;
        try {
            newUri = new URI("https", "", uri.getHost(), uri.getPort(), "/", "", "");
        } catch (URISyntaxException e) {
            return new FailedIoFuture<>(new IOException(e));
        }
        final OptionMap modifiedOptions = OptionMap.builder().addAll(options).set(Options.SSL_STARTTLS, false).getMap();
        final Map<String, String> headers = new HashMap<String, String>();
        headers.put(UPGRADE, "jboss-remoting");
        final String secKey = createSecKey();
        headers.put(SEC_JBOSS_REMOTING_KEY, secKey);

        final FutureResult<SslConnection> future = new FutureResult<>(getExecutor());

        // TODO: perform upgrade using existing SSL connection
        HttpUpgrade.performUpgrade(getXnioWorker(), null, null, newUri, headers, channel -> {
            ChannelListeners.invokeChannelListener(channel, openListener);
        }, null, modifiedOptions, new RemotingHandshakeChecker(secKey))
                .addNotifier((ioFuture, attachment) -> {
                    if (ioFuture.getStatus() == IoFuture.Status.FAILED) {
                        future.setException(ioFuture.getException());
                    }
                }, null);

        return future.getIoFuture();
    }

    private class RemotingHandshakeChecker implements HandshakeChecker {

        private final String key;

        private RemotingHandshakeChecker(final String key) {
            this.key = key;
        }

        @Override
        public void checkHandshake(final Map<String, String> headers) throws IOException {
            if(!headers.containsKey(SEC_JBOSS_REMOTING_ACCEPT)) {
                throw new IOException("No " + SEC_JBOSS_REMOTING_ACCEPT + " header in response");
            }
            final String expectedResponse = createExpectedResponse(key);
            final String response = headers.get(SEC_JBOSS_REMOTING_ACCEPT);
            if(!response.equals(expectedResponse)) {
                throw new IOException(SEC_JBOSS_REMOTING_ACCEPT + " value of " + response + " did not match expected " + expectedResponse);
            }
        }
    }

    public ProviderInterface getProviderInterface() {
        return providerInterface;
    }

    final class ProviderInterface implements ExternalConnectionProvider {

        public ConnectionAdaptorImpl createConnectionAdaptor(final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory) throws IOException {
            return new ConnectionAdaptorImpl(optionMap, saslAuthenticationFactory);
        }
    }

    private final class ConnectionAdaptorImpl implements Consumer<StreamConnection> {
        private final OptionMap optionMap;
        private final SaslAuthenticationFactory saslAuthenticationFactory;

        ConnectionAdaptorImpl(final OptionMap optionMap, final SaslAuthenticationFactory saslAuthenticationFactory) {
            this.optionMap = optionMap;
            // TODO: server name, protocol name
            this.saslAuthenticationFactory = saslAuthenticationFactory;
        }

        public void accept(final StreamConnection channel) {
            if (channel.getWorker() != getXnioWorker()) {
                throw RemoteLogger.log.invalidWorker();
            }

            try {
                channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
            } catch (IOException e) {
                // ignore
            }

            final SslChannel sslChannel = channel instanceof SslConnection ? (SslConnection) channel : null;
            final ConduitStreamSourceChannel sourceChannel = channel.getSourceChannel();
            final RemoteConnection connection = new RemoteConnection(channel, new MessageReader(sourceChannel), sslChannel, optionMap, HttpUpgradeConnectionProvider.this);
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, getConnectionProviderContext(), saslAuthenticationFactory, optionMap);
            channel.getSinkChannel().setWriteListener(connection.getWriteListener());
            RemoteLogger.log.tracef("Accepted connection from %s to %s", channel.getPeerAddress(), channel.getLocalAddress());
            openListener.handleEvent(sourceChannel);
        }
    }

    protected String createSecKey() {
        SecureRandom random = new SecureRandom();
        byte[] data = new byte[16];
        for (int i = 0; i < 4; ++i) {
            int val = random.nextInt();
            data[i * 4] = (byte) val;
            data[i * 4 + 1] = (byte) ((val >> 8) & 0xFF);
            data[i * 4 + 2] = (byte) ((val >> 16) & 0xFF);
            data[i * 4 + 3] = (byte) ((val >> 24) & 0xFF);
        }
        return Base64.getEncoder().encodeToString(data);
    }

    protected String createExpectedResponse(String secKey) throws IOException {
        try {
            final String concat = secKey + MAGIC_NUMBER;
            final MessageDigest digest = MessageDigest.getInstance("SHA1");

            digest.update(concat.getBytes("UTF-8"));
            final byte[] bytes = digest.digest();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }

    }
}

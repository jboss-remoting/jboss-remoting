/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import static org.jboss.remoting3._private.Messages.conn;
import static org.xnio.IoUtils.safeClose;

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

import javax.net.ssl.SSLContext;

import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ExternalConnectionProvider;
import org.wildfly.common.Assert;
import org.wildfly.security.auth.client.AuthenticationConfiguration;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.Cancellable;
import org.xnio.ChannelListener;
import org.xnio.ChannelListeners;
import org.xnio.FailedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.StreamConnection;
import org.xnio.channels.SslChannel;
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

    HttpUpgradeConnectionProvider(final OptionMap optionMap, final ConnectionProviderContext connectionProviderContext, final String protocolName) throws IOException {
        super(optionMap, connectionProviderContext, protocolName);
    }

    protected IoFuture<StreamConnection> createConnection(final URI uri, final InetSocketAddress bindAddress, final InetSocketAddress destination, final OptionMap connectOptions,
            final ChannelListener<StreamConnection> openListener) {
        final URI newUri;
        try {
            newUri = new URI("http", "", uri.getHost(), uri.getPort(), "/", "", "");
        } catch (URISyntaxException e) {
            return new FailedIoFuture<>(new IOException(e));
        }

        final FutureResult<StreamConnection> returnedFuture = new FutureResult<>(getExecutor());

        ChannelListener<StreamConnection> upgradeListener = new UpgradeListener<StreamConnection>(StreamConnection.class, newUri, openListener, returnedFuture);
        IoFuture<StreamConnection> rawFuture = super.createConnection(uri, bindAddress, destination, connectOptions, upgradeListener);
        rawFuture.addNotifier( new IoFuture.HandlingNotifier<StreamConnection, FutureResult<StreamConnection>>() {

            @Override
            public void handleCancelled(FutureResult<StreamConnection> attachment) {
                attachment.setCancelled();
            }

            @Override
            public void handleFailed(IOException exception, FutureResult<StreamConnection> attachment) {
                attachment.setException(exception);
            }

        } , returnedFuture);

        return returnedFuture.getIoFuture();
    }

    protected IoFuture<SslConnection> createSslConnection(final URI uri, final InetSocketAddress bindAddress, final InetSocketAddress destination, final OptionMap options,
            final AuthenticationConfiguration configuration, final SSLContext sslContext, final ChannelListener<StreamConnection> openListener) {
        final URI newUri;
        try {
            newUri = new URI("https", "", uri.getHost(), uri.getPort(), "/", "", "");
        } catch (URISyntaxException e) {
            return new FailedIoFuture<>(new IOException(e));
        }

        final FutureResult<SslConnection> returnedFuture = new FutureResult<>(getExecutor());
        final OptionMap modifiedOptions = OptionMap.builder().addAll(options).set(Options.SSL_STARTTLS, false).getMap();

        ChannelListener<StreamConnection> upgradeListener = new UpgradeListener<SslConnection>(SslConnection.class, newUri, openListener, returnedFuture);
        IoFuture<SslConnection> rawFuture = super.createSslConnection(uri, bindAddress, destination, modifiedOptions, configuration, sslContext, upgradeListener);
        rawFuture.addNotifier( new IoFuture.HandlingNotifier<StreamConnection, FutureResult<SslConnection>>() {

            @Override
            public void handleCancelled(FutureResult<SslConnection> attachment) {
                attachment.setCancelled();
            }

            @Override
            public void handleFailed(IOException exception, FutureResult<SslConnection> attachment) {
                attachment.setException(exception);
            }

        } , returnedFuture);

        return returnedFuture.getIoFuture();
    }

    private static class UpgradeListener<T extends StreamConnection> implements ChannelListener<StreamConnection> {

        private final Class<T> type;
        private final URI uri;
        private final ChannelListener<StreamConnection> openListener;
        private final FutureResult<T> futureResult;

        UpgradeListener(Class<T> type, URI uri, ChannelListener<StreamConnection> openListener, FutureResult<T> futureResult) {
            this.type = type;
            this.uri = uri;
            this.openListener = openListener;
            this.futureResult = futureResult;
        }

        @Override
        public void handleEvent(StreamConnection channel) {
            final Map<String, String> headers = new HashMap<String, String>();
            headers.put(UPGRADE, "jboss-remoting");
            final String secKey = createSecKey();
            headers.put(SEC_JBOSS_REMOTING_KEY, secKey);

            IoFuture<T> upgradeFuture = HttpUpgrade.performUpgrade(type.cast(channel), uri, headers, upgradeChannel -> {
                ChannelListeners.invokeChannelListener(upgradeChannel, openListener);
            }, new RemotingHandshakeChecker(secKey));

            futureResult.addCancelHandler(new Cancellable() {
                @Override
                public Cancellable cancel() {
                    if (channel.isOpen()) {
                        safeClose(channel);
                    }
                    return this;
                }
            });

            upgradeFuture.addNotifier( new IoFuture.HandlingNotifier<T, FutureResult<T>>() {

                @Override
                public void handleCancelled(FutureResult<T> attachment) {
                    attachment.setCancelled();
                }

                @Override
                public void handleFailed(IOException exception, FutureResult<T> attachment) {
                    attachment.setException(exception);
                }

                @Override
                public void handleDone(T data, FutureResult<T> attachment) {
                    attachment.setResult(data);
                }

            }, futureResult);
        }

    }

    private static class RemotingHandshakeChecker implements HandshakeChecker {

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
            Assert.checkNotNullParam("optionMap", optionMap);
            Assert.checkNotNullParam("saslAuthenticationFactory", saslAuthenticationFactory);
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
                throw conn.invalidWorker();
            }

            try {
                channel.setOption(Options.TCP_NODELAY, Boolean.TRUE);
            } catch (IOException e) {
                // ignore
            }

            final SslChannel sslChannel = channel instanceof SslConnection ? (SslConnection) channel : null;
            final RemoteConnection connection = new RemoteConnection(channel, sslChannel, optionMap, HttpUpgradeConnectionProvider.this);
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, getConnectionProviderContext(), saslAuthenticationFactory, optionMap);
            channel.getSinkChannel().setWriteListener(connection.getWriteListener());
            channel.getSinkChannel().setCloseListener(c -> connection.getWriteListener().shutdownWrites());
            conn.tracef("Accepted connection from %s to %s", channel.getPeerAddress(), channel.getLocalAddress());
            openListener.handleEvent(channel.getSourceChannel());
        }
    }

    protected static String createSecKey() {
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

    protected static String createExpectedResponse(String secKey) throws IOException {
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

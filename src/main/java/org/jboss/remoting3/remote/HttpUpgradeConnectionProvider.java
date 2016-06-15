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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.AccessController;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedExceptionAction;
import java.security.SecureRandom;
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
            final RemoteConnection connection = new RemoteConnection(channel, sslChannel, optionMap, HttpUpgradeConnectionProvider.this);
            final ServerConnectionOpenListener openListener = new ServerConnectionOpenListener(connection, getConnectionProviderContext(), saslAuthenticationFactory, optionMap);
            channel.getSinkChannel().setWriteListener(connection.getWriteListener());
            RemoteLogger.log.tracef("Accepted connection from %s to %s", channel.getPeerAddress(), channel.getLocalAddress());
            openListener.handleEvent(channel.getSourceChannel());
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
        return FlexBase64.encodeString(data, false);
    }

    protected String createExpectedResponse(String secKey) throws IOException {
        try {
            final String concat = secKey + MAGIC_NUMBER;
            final MessageDigest digest = MessageDigest.getInstance("SHA1");

            digest.update(concat.getBytes("UTF-8"));
            final byte[] bytes = digest.digest();
            return FlexBase64.encodeString(bytes, false);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }

    }

    private static class FlexBase64 {
        /*
         * Note that this code heavily favors performance over reuse and clean style.
         */

        private static final byte[] ENCODING_TABLE;
        private static final byte[] DECODING_TABLE = new byte[80];
        private static final Constructor<String> STRING_CONSTRUCTOR;

        static {
            try {
                ENCODING_TABLE = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes("ASCII");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException();
            }

            for (int i = 0; i < ENCODING_TABLE.length; i++) {
                int v = (ENCODING_TABLE[i] & 0xFF) - 43;
                DECODING_TABLE[v] = (byte) (i + 1);  // zero = illegal
            }

            Constructor<String> c = null;
            try {
                PrivilegedExceptionAction<Constructor<String>> runnable = () -> {
                    Constructor<String> c1 = String.class.getDeclaredConstructor(char[].class, boolean.class);
                    c1.setAccessible(true);
                    return c1;
                };
                if (System.getSecurityManager() != null) {
                    c = AccessController.doPrivileged(runnable);
                } else {
                    c = runnable.run();
                }
            } catch (Throwable t) {
            }

            STRING_CONSTRUCTOR = c;
        }

        /**
         * Encodes a fixed and complete byte array into a Base64 String.
         *
         * @param source the byte array to encode from
         * @param wrap   whether or not to wrap the output at 76 chars with CRLFs
         * @return a new String representing the Base64 output
         */
        public static String encodeString(byte[] source, boolean wrap) {
            return encodeString(source, 0, source.length, wrap);
        }


        private static String encodeString(byte[] source, int pos, int limit, boolean wrap) {
            int olimit = (limit - pos);
            int remainder = olimit % 3;
            olimit = (olimit + (remainder == 0 ? 0 : 3 - remainder)) / 3 * 4;
            olimit += (wrap ? (olimit / 76) * 2 + 2 : 0);
            char[] target = new char[olimit];
            int opos = 0;
            int last = 0;
            int count = 0;
            int state = 0;
            final byte[] ENCODING_TABLE = FlexBase64.ENCODING_TABLE;

            while (limit > pos) {
                //  ( 6 | 2) (4 | 4) (2 | 6)
                int b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[b >>> 2];
                last = (b & 0x3) << 4;
                if (pos >= limit) {
                    state = 1;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 4)];
                last = (b & 0x0F) << 2;
                if (pos >= limit) {
                    state = 2;
                    break;
                }
                b = source[pos++] & 0xFF;
                target[opos++] = (char) ENCODING_TABLE[last | (b >>> 6)];
                target[opos++] = (char) ENCODING_TABLE[b & 0x3F];

                if (wrap) {
                    count += 4;
                    if (count >= 76) {
                        count = 0;
                        target[opos++] = 0x0D;
                        target[opos++] = 0x0A;
                    }
                }
            }

            complete(target, opos, state, last, wrap);

            try {
                // Eliminate copying on Open/Oracle JDK
                if (STRING_CONSTRUCTOR != null) {
                    return STRING_CONSTRUCTOR.newInstance(target, Boolean.TRUE);
                }
            } catch (Exception e) {
            }

            return new String(target);
        }

        private static int complete(char[] target, int pos, int state, int last, boolean wrap) {
            if (state > 0) {
                target[pos++] = (char) ENCODING_TABLE[last];
                for (int i = state; i < 3; i++) {
                    target[pos++] = '=';
                }
            }
            if (wrap) {
                target[pos++] = 0x0D;
                target[pos++] = 0x0A;
            }

            return pos;
        }

    }

}

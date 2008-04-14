package org.jboss.cx.remoting.http.urlconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.http.spi.AbstractIncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.ByteMessageInput;
import org.jboss.cx.remoting.spi.ByteMessageOutput;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class HttpUrlChannel {

    private static final Logger log = Logger.getLogger(HttpUrlChannel.class);

    private final ConcurrentMap<String, String> cookies = CollectionUtil.synchronizedMap(new LinkedHashMap<String, String>());

    private RemotingHttpSessionContext sessionContext;

    public RemotingHttpSessionContext getSessionContext() {
        return sessionContext;
    }

    public void setSessionContext(final RemotingHttpSessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    public void doIt() {
        final URL connectUrl = null;
        new Runnable() {
            public void run() {
                for (;;) {
                    final OutgoingHttpMessage message = sessionContext.getOutgoingHttpMessage();
                    HttpURLConnection httpConnection = null;
                    try {
                        httpConnection = (HttpURLConnection) connectUrl.openConnection();
                        httpConnection.setDoInput(true);
                        httpConnection.setDoOutput(true);
                        httpConnection.setDefaultUseCaches(false);
                        httpConnection.setUseCaches(false);
                        httpConnection.setInstanceFollowRedirects(false);
//                        httpURLConnection.setConnectTimeout();
//                        httpURLConnection.setReadTimeout();
                        httpConnection.setRequestMethod("POST"); // All remoting requests are POST
                        for (Map.Entry<String, String> entry : cookies.entrySet()) {
//                            httpConnection.setRequestProperty();
//                            entry.getKey()
                        }
                        httpConnection.connect();
                        final OutputStream outputStream = httpConnection.getOutputStream();
                        message.writeMessageData(new ByteMessageOutput() {

                            public void write(final int b) throws IOException {
                                outputStream.write(b);
                            }

                            public void write(final byte[] b) throws IOException {
                                outputStream.write(b);
                            }

                            public void write(final byte[] b, final int offs, final int len) throws IOException {
                                outputStream.write(b, offs, len);
                            }

                            public void commit() throws IOException {
                            }

                            public int getBytesWritten() throws IOException {
                                throw new UnsupportedOperationException("getBytesWritten()");
                            }

                            public void close() throws IOException {
                                outputStream.close();
                            }

                            public void flush() throws IOException {
                                outputStream.flush();
                            }
                        });
                        // now read the reply
                        final List<String> setCookies = httpConnection.getHeaderFields().get("Set-Cookie");
                        final InputStream inputStream = httpConnection.getInputStream();
                        sessionContext.processInboundMessage(new AbstractIncomingHttpMessage() {
                            public ByteMessageInput getMessageData() throws IOException {
                                return new ByteMessageInput() {

                                    public int read() throws IOException {
                                        return inputStream.read();
                                    }

                                    public int read(final byte[] data) throws IOException {
                                        return inputStream.read(data);
                                    }

                                    public int read(final byte[] data, final int offs, final int len) throws IOException {
                                        return inputStream.read(data, offs, len);
                                    }

                                    public int remaining() {
                                        throw new UnsupportedOperationException("remaining()");
                                    }

                                    public void close() throws IOException {
                                        inputStream.close();
                                    }
                                };
                            }
                        });
                    } catch (IOException e) {
                        // probably a HTTP error occurred, so let's consume it
                        try {
                            if (httpConnection != null) {
                                final int responseCode = httpConnection.getResponseCode();
                                log.trace("Got error response code %d from remote URL '%s'", Integer.valueOf(responseCode), connectUrl);
                                final InputStream errorStream = httpConnection.getErrorStream();
                                // consume & discard the error stream
                                while (errorStream.read() > -1);
                                errorStream.close();
                            }
                        } catch (IOException e2) {
                            log.trace("Error consuming the error stream from remote URL '%s'", connectUrl);
                        }
                        // todo - need a backoff timer to prevent a storm of HTTP errors.  Or perhaps the session should be torn down.
                    }
                }
            }
        };

    }
}

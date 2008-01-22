package org.jboss.cx.remoting.http.se6;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.AbstractIncomingHttpMessage;
import org.jboss.cx.remoting.core.util.ByteInput;
import org.jboss.cx.remoting.core.util.ByteOutput;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

/**
 *
 */
public final class ServerInstance {
    private final HttpServer httpServer;
    private final InetAddress localAddress;
    private final int localPort;

    public ServerInstance(String context, HttpServer httpServer) {
        this.httpServer = httpServer;
        final HttpContext httpContext = httpServer.createContext(context, new MyHttpHandler());
        httpContext.setAuthenticator(new BasicAuthenticator("Remote Access") {
            public boolean checkCredentials(final String user, final String password) {
                final char[] passwordChars = password.toCharArray();

                // todo - use endpoint callbacks
                return false;
            }
        });
        final InetSocketAddress socketAddress = httpServer.getAddress();
        localAddress = socketAddress.getAddress();
        localPort = socketAddress.getPort();
    }

    public ServerInstance(String context, InetSocketAddress address, Executor executor) throws IOException {
        this(context, HttpServer.create(address, 0));
        httpServer.setExecutor(executor);
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        // todo - magic #
        httpServer.stop(30);
    }

    private class MyHttpHandler implements HttpHandler {
        public void handle(final HttpExchange httpExchange) throws IOException {
            final URI requestURI = httpExchange.getRequestURI();
            final Headers requestHeaders = httpExchange.getRequestHeaders();
            final InetSocketAddress inetSocketAddress = httpExchange.getRemoteAddress();
            final InetAddress remoteAddress = inetSocketAddress.getAddress();
            final int remotePort = inetSocketAddress.getPort();
            RemotingHttpSessionContext httpSessionContext = null; // todo locate
            httpSessionContext.queueMessage(new AbstractIncomingHttpMessage(localAddress, localPort, remoteAddress, remotePort) {
                public ByteInput getMessageData() {
                    final InputStream inputStream = httpExchange.getRequestBody();
                    return new ByteInput() {
                        public int read() throws IOException {
                            return inputStream.read();
                        }

                        public int read(byte[] data) throws IOException {
                            return inputStream.read(data);
                        }

                        public int read(byte[] data, int offs, int len) throws IOException {
                            return inputStream.read(data, offs, len);
                        }

                        public int remaining() {
                            return -1;
                        }

                        public void close() throws IOException {
                            inputStream.close();
                        }
                    };
                }
            });
            // todo - WAIT untit the input stream is consumed? or - just don't close the output until the input is done
            // todo - consume all of input stream
            OutgoingHttpMessage httpReply = null;
            try {
                // todo - magic # - timeout should be configurable
                httpReply = httpSessionContext.getNextMessage(8000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (httpReply == null) {
                // send empty OK
                httpExchange.sendResponseHeaders(200, 0);
            } else {
                // send reply
                final Headers responseHeaders = httpExchange.getResponseHeaders();
                for (final String name : httpReply.getHeaderNames()) {
                    for (final String value : httpReply.getHeaderValues(name)) {
                        responseHeaders.set(name, value);
                    }
                }
                httpExchange.sendResponseHeaders(200, 0); // todo - preset response size?
                final OutputStream outputStream = httpExchange.getResponseBody();
                httpReply.writeMessageData(new ByteOutput() {
                    public void write(int b) throws IOException {
                        outputStream.write(b);
                    }

                    public void write(byte[] b) throws IOException {
                        outputStream.write(b);
                    }

                    public void write(byte[] b, int offs, int len) throws IOException {
                        outputStream.write(b, offs, len);
                    }

                    public void commit() throws IOException {
                    }

                    public int getBytesWritten() throws IOException {
                        return -1;
                    }

                    public void close() throws IOException {
                        outputStream.close();
                    }

                    public void flush() throws IOException {
                        outputStream.flush();
                    }
                });
            }
            httpExchange.close();
        }
    }
}

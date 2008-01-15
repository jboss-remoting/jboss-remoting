package org.jboss.cx.remoting.http.se6;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.InetAddress;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.concurrent.Executor;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.BasicAuthenticator;

import org.jboss.cx.remoting.http.spi.HttpRemotingSessionContext;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.IncomingHttpMessage;

/**
 *
 */
public final class ServerInstance {
    private final HttpServer httpServer;

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
            HttpRemotingSessionContext sessionContext = null; // todo locate
            sessionContext.queueMessage(new IncomingHttpMessage() {
                public InputStream getMessageData() {
                    return null;
                }

                public InetAddress getLocalAddress() {
                    return null;
                }

                public int getLocalPort() {
                    return 0;
                }
            });
            // todo - WAIT untit the input stream is consumed? or - just don't close the output until the input is done
            // todo - consume all of input stream
            OutgoingHttpMessage httpReply = null;
            try {
                httpReply = sessionContext.getNextMessage(8000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (httpReply == null) {
                // send empty OK
            } else {
                // send reply
                final Headers responseHeaders = httpExchange.getResponseHeaders();
                httpExchange.sendResponseHeaders(200, 0); // todo - preset response size?
                final OutputStream outputStream = httpExchange.getResponseBody();
                httpReply.writeTo(outputStream);
            }
            httpExchange.close();
        }
    }
}

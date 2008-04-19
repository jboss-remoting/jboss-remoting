package org.jboss.cx.remoting.http.se6;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentMap;
import java.security.SecureRandom;
import org.jboss.cx.remoting.http.AbstractHttpChannel;
import org.jboss.cx.remoting.http.HttpMessageWriter;
import org.jboss.cx.remoting.http.cookie.Cookie;
import org.jboss.cx.remoting.http.cookie.CookieParser;
import org.jboss.cx.remoting.http.RemotingHttpChannelContext;
import org.jboss.cx.remoting.http.RemotingHttpServerContext;
import org.jboss.cx.remoting.util.AbstractOutputStreamByteMessageOutput;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.IoUtil;
import org.jboss.cx.remoting.util.InputStreamByteMessageInput;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 *
 */
public final class SunHttpServerChannel extends AbstractHttpChannel implements HttpHandler {

    public SunHttpServerChannel() {
    }

    // Configuration

    private CookieParser cookieParser;

    public CookieParser getCookieParser() {
        return cookieParser;
    }

    public void setCookieParser(final CookieParser cookieParser) {
        this.cookieParser = cookieParser;
    }

    // Dependencies

    private RemotingHttpServerContext serverContext;
    private HttpContext httpContext;
    private Random random;

    public RemotingHttpServerContext getServerContext() {
        return serverContext;
    }

    public void setServerContext(final RemotingHttpServerContext serverContext) {
        this.serverContext = serverContext;
    }

    public HttpContext getHttpContext() {
        return httpContext;
    }

    public void setHttpContext(final HttpContext httpContext) {
        this.httpContext = httpContext;
    }

    public Random getRandom() {
        return random;
    }

    public void setRandom(final Random random) {
        this.random = random;
    }

    // Lifecycle

    public void create() {
        if (serverContext == null) {
            throw new NullPointerException("serverContext is null");
        }
        if (random == null) {
            random = new SecureRandom();
        }
    }

    public void start() {
        httpContext.setHandler(this);
    }

    public void stop() {
        httpContext.setHandler(new HttpHandler() {
            public void handle(final HttpExchange exchange) throws IOException {
                throw new IOException("Context is not available");
            }
        });
    }

    public void destroy() {
        serverContext = null;
        httpContext = null;
        random = null;
    }

    // Implementation

    private final ConcurrentMap<String, RemotingHttpChannelContext> sessions = CollectionUtil.concurrentMap();

    public void handle(final HttpExchange exchange) throws IOException {
        // it could be a non-https exchange (in the case of a separate SSL frontend)
        final boolean secure = "https".equals(exchange.getProtocol());
        final Headers requestHeader = exchange.getRequestHeaders();
        final List<String> cookieHeaders = requestHeader.get("Cookie");
        int parkTimeout = -1;
        String sessionId = null;
        for (String cookieString : cookieHeaders) {
            final List<Cookie> cookies = cookieParser.parseCookie(cookieString);
            for (Cookie cookie : cookies) {
                if ("Park-Timeout".equals(cookie.getName())) {
                    try {
                        parkTimeout = Integer.parseInt(cookie.getValue());
                    } catch (NumberFormatException e) {
                        // oh well
                    }
                } else if ("JSESSIONID".equals(cookie.getName())) {
                    sessionId = cookie.getValue();
                }
            }
        }
        final boolean needToSetSession;
        RemotingHttpChannelContext context = sessions.get(sessionId);
        final InputStream inputStream = exchange.getRequestBody();
        try {
            if (context == null) {
                needToSetSession = true;
                context = serverContext.processUnsolicitedInboundMessage(new InputStreamByteMessageInput(inputStream, -1));
            } else {
                needToSetSession = false;
                context.processInboundMessage(new InputStreamByteMessageInput(inputStream, -1));
            }
        } finally {
            IoUtil.closeSafely(inputStream);
        }
        if (needToSetSession) {
            final StringBuilder setCookieBuilder = new StringBuilder(60);
            setCookieBuilder.append("JSESSIONID=");
            for (;;) {
                String jsessionid = generateSessionId();
                if (sessions.putIfAbsent(jsessionid, context) == null) {
                    setCookieBuilder.append(jsessionid);
                    break;
                }
            }
            if (secure) {
                setCookieBuilder.append("; secure");
            }
            exchange.getResponseHeaders().set("Set-Cookie", setCookieBuilder.toString());
        }
        final HttpMessageWriter messageWriter = context.waitForOutgoingHttpMessage(parkTimeout);
        final OutputStream outputStream = exchange.getResponseBody();
        try {
            messageWriter.writeMessageData(new AbstractOutputStreamByteMessageOutput(outputStream) {
                public void commit() throws IOException {
                }
            });
        } finally {
            IoUtil.closeSafely(outputStream);
        }
    }

    private String generateSessionId() {
        final byte[] bytes = new byte[16];
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        random.nextBytes(bytes);
        for (byte b : bytes) {
            builder.append(Character.forDigit(b >>> 4 & 15, 16));
            builder.append(Character.forDigit(b & 15, 16));
        }
        return builder.toString();
    }
}

package org.jboss.cx.remoting.http.urlconnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.cx.remoting.http.cookie.CookieClientSession;
import org.jboss.cx.remoting.http.spi.AbstractIncomingHttpMessage;
import org.jboss.cx.remoting.http.spi.OutgoingHttpMessage;
import org.jboss.cx.remoting.http.spi.RemotingHttpSessionContext;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AbstractOutputStreamByteMessageOutput;
import org.jboss.cx.remoting.util.ByteMessageInput;
import org.jboss.cx.remoting.util.InputStreamByteMessageInput;
import org.jboss.cx.remoting.util.IoUtil;

/**
 *
 */
public abstract class AbstractHttpUrlChannel {

    private static final Logger log = Logger.getLogger(AbstractHttpUrlChannel.class);

    private final CookieClientSession cookieClientSession = new CookieClientSession();

    protected AbstractHttpUrlChannel() {
    }

    // Configuration

    private int concurrentRequests = 2;
    private int connectTimeout = 5000;
    private int readTimeout = 5000;
    private URL connectUrl;

    public int getConcurrentRequests() {
        return concurrentRequests;
    }

    public void setConcurrentRequests(final int concurrentRequests) {
        this.concurrentRequests = concurrentRequests;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(final int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(final int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public URL getConnectUrl() {
        return connectUrl;
    }

    public void setConnectUrl(final URL connectUrl) {
        this.connectUrl = connectUrl;
    }

    // Dependencies

    private RemotingHttpSessionContext sessionContext;
    private Executor executor;

    public RemotingHttpSessionContext getSessionContext() {
        return sessionContext;
    }

    public void setSessionContext(final RemotingHttpSessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    // Lifecycle

    private ExecutorService executorService;

    public void create() {
        if (executor == null) {
            executor = executorService = Executors.newFixedThreadPool(concurrentRequests);
        }
        if (connectUrl == null) {
            throw new NullPointerException("connectUrl is null");
        }
        if (sessionContext == null) {
            throw new NullPointerException("sessionContext is null");
        }
    }

    public void start() {

    }

    public void stop() {

    }

    public void destroy() {
        try {

        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        executor = executorService = null;
        sessionContext = null;
    }

    // Interface

    protected void handleRequest(final URL connectUrl) {
        final RemotingHttpSessionContext sessionContext = getSessionContext();
        final OutgoingHttpMessage message = sessionContext.getOutgoingHttpMessage();
        try {
            final HttpURLConnection httpConnection = intializeConnection(connectUrl);
            try {
                httpConnection.connect();
                final OutputStream outputStream = httpConnection.getOutputStream();
                try {
                    message.writeMessageData(new AbstractOutputStreamByteMessageOutput(outputStream) {
                        public void commit() throws IOException {
                        }
                    });
                    // now read the reply
                    final String responseMessage = httpConnection.getResponseMessage();
                    log.trace("HTTP server sent back a response message: %s", responseMessage);
                    final List<String> setCookies = httpConnection.getHeaderFields().get("Set-Cookie");
                    for (String s : setCookies) {
                        cookieClientSession.handleSetCookieHeader(s, connectUrl.getHost(), connectUrl.getPath());
                    }
                    final InputStream inputStream = httpConnection.getInputStream();
                    try {
                        sessionContext.processInboundMessage(new AbstractIncomingHttpMessage() {
                            public ByteMessageInput getMessageData() throws IOException {
                                return new InputStreamByteMessageInput(inputStream, -1);
                            }
                        });
                    } finally {
                        IoUtil.closeSafely(inputStream);
                    }
                } finally {
                    IoUtil.closeSafely(outputStream);
                }
            } catch (IOException e) {
                // probably a HTTP error occurred, so let's consume it
                try {
                    final InputStream errorStream = httpConnection.getErrorStream();
                    if (errorStream != null) try {
                        // consume & discard the error stream
                        while (errorStream.read() > -1);
                        errorStream.close();
                    } finally {
                        IoUtil.closeSafely(errorStream);
                    } else {
                        log.trace(e, "Connection failed but there is no error stream");
                    }
                } catch (IOException e2) {
                    log.trace(e2, "Error consuming the error stream from remote URL '%s'", connectUrl);
                }
                // todo - need a backoff timer to prevent a storm of HTTP errors.  Or perhaps the session should be torn down.
            }
        } catch (IOException e) {
            log.trace(e, "Error establishing connection");
        }
    }

    protected HttpURLConnection intializeConnection(final URL connectUrl) throws IOException {
        final HttpURLConnection httpConnection = (HttpURLConnection) connectUrl.openConnection();
        httpConnection.setDoInput(true);
        httpConnection.setDoOutput(true);
        httpConnection.setDefaultUseCaches(false);
        httpConnection.setUseCaches(false);
        httpConnection.setInstanceFollowRedirects(false);
        httpConnection.setConnectTimeout(getConnectTimeout());
        httpConnection.setReadTimeout(getReadTimeout());
        httpConnection.setRequestMethod("POST"); // All remoting requests are POST
        final String cookieHeader = cookieClientSession.getCookieHeader(connectUrl.getHost(), connectUrl.getPath(), false);
        if (cookieHeader != null) {
            httpConnection.setRequestProperty("Cookie", cookieHeader);
        }
        return httpConnection;
    }
}

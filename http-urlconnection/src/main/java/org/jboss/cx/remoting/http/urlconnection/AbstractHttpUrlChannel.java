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
import java.util.concurrent.FutureTask;
import java.util.concurrent.Future;
import org.jboss.cx.remoting.http.AbstractHttpChannel;
import org.jboss.cx.remoting.http.cookie.CookieClientSession;
import org.jboss.cx.remoting.http.HttpMessageWriter;
import org.jboss.cx.remoting.http.RemotingHttpChannelContext;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AbstractOutputStreamByteMessageOutput;
import org.jboss.cx.remoting.util.IoUtil;
import org.jboss.cx.remoting.util.NamingThreadFactory;
import org.jboss.cx.remoting.util.InputStreamByteMessageInput;

/**
 *
 */
public abstract class AbstractHttpUrlChannel extends AbstractHttpChannel {

    private static final Logger log = Logger.getLogger(AbstractHttpUrlChannel.class);

    private final CookieClientSession cookieClientSession = new CookieClientSession();

    protected AbstractHttpUrlChannel() {
    }

    // Configuration

    private int concurrentRequests = 2;
    private int connectTimeout = 5000;
    private int readTimeout = 0;  // Default to unlimited to support "parking" the connection at the other end
    private int errorBackoffTime = 5000;
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

    public int getErrorBackoffTime() {
        return errorBackoffTime;
    }

    public void setErrorBackoffTime(final int errorBackoffTime) {
        this.errorBackoffTime = errorBackoffTime;
    }

    public URL getConnectUrl() {
        return connectUrl;
    }

    public void setConnectUrl(final URL connectUrl) {
        this.connectUrl = connectUrl;
    }

    // Dependencies

    private Executor executor;

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    // Lifecycle

    private ExecutorService executorService;
    private Future[] futures;

    public void create() {
        super.create();
        if (executor == null) {
            executor = executorService = Executors.newFixedThreadPool(concurrentRequests, new NamingThreadFactory(Executors.defaultThreadFactory(), "Remoting HTTP client %s"));
        }
        if (connectUrl == null) {
            throw new NullPointerException("connectUrl is null");
        }
    }

    public void start() {
        final Future[] futures = new Future[concurrentRequests];
        for (int i = 0; i < futures.length; i++) {
            final FutureTask task = new FutureTask<Void>(null) {
                public void run() {
                    while (! isCancelled()) try {
                        handleRequest();
                    } catch (Throwable t) {
                        log.trace(t, "Request hander failed");
                    }
                }
            };
            executor.execute(task);
            futures[i] = task;
        }
        this.futures = futures;
    }

    public void stop() {
        if (futures != null) {
            final Future[] futures = this.futures;
            this.futures = null;
            for (Future future : futures) try {
                future.cancel(true);
            } catch (Throwable t) {
                log.trace(t, "Error cancelling task");
            }
        }
    }

    public void destroy() {
        try {
            super.destroy();
        } finally {
            if (executorService != null) {
                executorService.shutdown();
            }
        }
        executor = executorService = null;
    }

    // Interface

    protected void handleRequest() {
        final URL connectUrl = getConnectUrl();
        final RemotingHttpChannelContext channelContext = getChannelContext();
        final int localParkTime = getLocalParkTime();
        final int remoteParkTime = getRemoteParkTime();
        final HttpMessageWriter messageWriter = channelContext.waitForOutgoingHttpMessage(localParkTime);
        try {
            final HttpURLConnection httpConnection = intializeConnection(connectUrl);
            try {
                if (remoteParkTime >= 0) {
                    httpConnection.addRequestProperty("Park-Timeout", Integer.toString(remoteParkTime));
                }
                httpConnection.connect();
                final OutputStream outputStream = httpConnection.getOutputStream();
                try {
                    messageWriter.writeMessageData(new AbstractOutputStreamByteMessageOutput(outputStream) {
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
                        channelContext.processInboundMessage(new InputStreamByteMessageInput(inputStream, -1));
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
                final int time = errorBackoffTime;
                if (time > 0) {
                    try {
                        log.debug("HTTP error occurred; backing off for %d milliseconds", Integer.valueOf(time));
                        Thread.sleep(time);
                    } catch (InterruptedException e1) {
                        log.trace("Thread interrupted while waiting for error backoff time to expire");
                        Thread.currentThread().interrupt();
                    }
                }
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

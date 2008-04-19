package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.http.spi.HttpChannel;
import org.jboss.cx.remoting.http.spi.RemotingHttpChannelContext;

/**
 *
 */
public abstract class AbstractHttpChannel implements HttpChannel {

    protected AbstractHttpChannel() {
    }

    // Configuration

    private int localParkTime = -1;
    private int remoteParkTime = -1;

    /**
     * Get the amount of time that a given channel member may be locally parked.  A value of {@code -1} means "park
     * indefinitely".  A value of {@code 0} means "do not park".  Otherwise the value is interpreted as milliseconds.
     *
     * In the case of an HTTP server, the minimum of this time and the client-requested timeout should be used.
     *
     * @return the local park time
     */
    public int getLocalParkTime() {
        return localParkTime;
    }

    /**
     * Set the amount of time that a given channel member may be locally parked.  A value of {@code -1} means "park
     * indefinitely".  A value of {@code 0} means "do not park".  Otherwise the value is interpreted as milliseconds.
     *
     * In the case of an HTTP server, the minimum of this time and the client-requested timeout should be used.
     *
     * @param localParkTime the local park time
     */
    public void setLocalParkTime(final int localParkTime) {
        this.localParkTime = localParkTime;
    }

    /**
     * Get the amount of time that a given channel member may be remotely parked.  A value of {@code -1} means "park
     * indefinitely".  A value of {@code 0} means "do not park".  Otherwise the value is interpreted as milliseconds.
     *
     * @return the remote park time
     */
    public int getRemoteParkTime() {
        return remoteParkTime;
    }

    /**
     * Set the amount of time that a given channel member may be remotely parked.  A value of {@code -1} means "park
     * indefinitely".  A value of {@code 0} means "do not park".  Otherwise the value is interpreted as milliseconds.
     *
     * @param remoteParkTime the remote park time
     */
    public void setRemoteParkTime(final int remoteParkTime) {
        this.remoteParkTime = remoteParkTime;
    }

    // Dependencies

    private RemotingHttpChannelContext channelContext;

    public RemotingHttpChannelContext getChannelContext() {
        return channelContext;
    }

    public void setChannelContext(final RemotingHttpChannelContext channelContext) {
        this.channelContext = channelContext;
    }

    // Lifecycle

    public void create() {
        if (channelContext == null) {
            throw new NullPointerException("channelContext is null");
        }
    }

    public void start() {

    }

    public void stop() {

    }

    public void destroy() {

    }
}

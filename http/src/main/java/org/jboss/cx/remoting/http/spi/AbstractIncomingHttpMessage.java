package org.jboss.cx.remoting.http.spi;

import java.net.InetAddress;

/**
 *
 */
public abstract class AbstractIncomingHttpMessage extends AbstractHttpMessage implements IncomingHttpMessage {
    private final InetAddress remoteAddress, localAddress;
    private final int localPort, remotePort;

    protected AbstractIncomingHttpMessage(final InetAddress localAddress, final int localPort, final InetAddress remoteAddress, final int remotePort) {
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.remotePort = remotePort;
    }

    public InetAddress getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public InetAddress getLocalAddress() {
        return localAddress;
    }

    public int getLocalPort() {
        return localPort;
    }
}

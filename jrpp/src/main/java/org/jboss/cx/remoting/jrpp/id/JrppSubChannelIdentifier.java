package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.core.util.Logger;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public abstract class JrppSubChannelIdentifier {
    private final short streamId;
    private final IdentifierManager manager;
    private final AtomicBoolean dead = new AtomicBoolean();

    private static final Logger log = Logger.getLogger(JrppSubChannelIdentifier.class);

    public JrppSubChannelIdentifier(IdentifierManager manager) throws IOException {
        this.manager = manager;
        streamId = this.manager.getIdentifier();
        if (streamId == 0) {
            throw new IOException("Channel is full");
        }
    }

    public synchronized void close() {
        if (! dead) {
            dead = true;
            manager.freeIdentifier(streamId);
        }
    }

    protected void finalize() throws Throwable {
        super.finalize();
        // for visibility of "dead"
        synchronized (this) {
            if (! dead) {
                log.trace("Leaked a subchannel instance");
                close();
            }
        }
    }

    public synchronized short getId() {
        if (dead) {
            throw new IllegalStateException("Read channel ID after close");
        }
        return streamId;
    }
}

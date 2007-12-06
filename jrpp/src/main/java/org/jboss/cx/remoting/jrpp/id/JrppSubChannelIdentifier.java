package org.jboss.cx.remoting.jrpp.id;

import org.jboss.cx.remoting.core.util.Logger;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public abstract class JrppSubChannelIdentifier {
    private final short id;
    private final AtomicBoolean dead = new AtomicBoolean(false);

    public JrppSubChannelIdentifier(short id) throws IOException {
        this.id = id;
    }

    public JrppSubChannelIdentifier(ObjectInputStream ois) throws IOException {
        id = ois.readShort();
    }

    public void release(IdentifierManager manager) {
        if (!dead.getAndSet(true)) {
            manager.freeIdentifier(id);
        }
    }

    public short getId() {
        if (dead.get()) {
            throw new IllegalStateException("Read channel ID after close");
        }
        return id;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof JrppSubChannelIdentifier)) return false;
        JrppSubChannelIdentifier other = (JrppSubChannelIdentifier) obj;
        return !(dead.get() || other.dead.get()) && other.id == id;
    }

    public int hashCode() {
        return id;
    }
}

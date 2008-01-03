package org.jboss.cx.remoting.jrpp.id;

import java.io.IOException;
import java.io.ObjectInput;
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

    public JrppSubChannelIdentifier(ObjectInput input) throws IOException {
        id = input.readShort();
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

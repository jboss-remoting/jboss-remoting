package org.jboss.cx.remoting.jrpp.id;

import java.util.BitSet;

/**
 *
 */
public final class IdentifierManager {
    private final BitSet bitSet = new BitSet(64);

    public synchronized short getIdentifier() {
        final int id = bitSet.nextClearBit(1);
        if (id > 0xffff) {
            return 0;
        } else {
            return (short) id;
        }
    }

    public synchronized void freeIdentifier(short id) {
        bitSet.clear(id & 0xffff);
    }

    public void getIdentifier(final short id) {
        
    }
}

package org.jboss.cx.remoting.http.id;

import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 */
public final class IdentifierProvider {
    private final Random rng;

    public IdentifierProvider(final Random rng) {
        this.rng = rng;
    }

    public AtomicLong newSequence() {
        return new AtomicLong(rng.nextLong());
    }
}

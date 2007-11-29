package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.Registration;

/**
 *
 */
public abstract class AbstractRegistration implements Registration {
    protected final Object sync = new Object();
    private int runCount;
    private boolean started;
    private boolean dead;
    private boolean stopping;

    protected AbstractRegistration() {
    }

    public final void start() {
        synchronized(sync) {
            if (started || stopping) {
                throw new IllegalStateException("Registration not stopped");
            }
            if (dead) {
                throw new IllegalStateException("Registration has been unregistered and may not be started again");
            }
            started = true;
            runCount = 0;
        }
    }

    public final void stop() {
        synchronized(sync) {
            if (! started) {
                throw new IllegalStateException("Registration not started");
            }
            started = false;
            if (runCount > 0) {
                stopping = true;
            }
        }
        signalShutdown();
        synchronized(sync) {
            if (stopping) {
                boolean intr = Thread.interrupted();
                try {
                    while (runCount > 0) {
                        try {
                            sync.wait();
                        } catch (InterruptedException e) {
                            intr = Thread.interrupted();
                        }
                    }
                } finally {
                    if (intr) {
                        Thread.currentThread().interrupt();
                    }
                }
                stopping = false;
            }
        }
    }

    public final void unregister() {
        synchronized(sync) {
            if (dead) {
                throw new IllegalStateException("Registration already unregistered");
            }
            if (started) {
                stop();
            }
            dead = true;
        }
        remove();
    }

    /**
     * Signal the shutdown of this registration.
     */
    protected abstract void signalShutdown();

    /**
     * Remove this registration after successful unregister.
     */
    protected abstract void remove();

    protected final void acquireForRun() {
        synchronized(sync) {
            if (! started) {
                throw new IllegalStateException("Registration not started");
            }
            runCount++;
        }
    }

    protected final void freeForRun() {
        synchronized(sync) {
            if (runCount == 0) {
                throw new IllegalStateException("More frees than acquires for registration");
            }
            runCount--;
            if (stopping) {
                sync.notify();
            }
        }
    }

    protected final boolean isStopped() {
        synchronized(sync) {
            return ! started && ! stopping && ! dead;
        }
    }
}

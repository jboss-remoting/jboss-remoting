package org.jboss.cx.remoting.core.util;

/**
 *
 */
public final class Resource {
    private int count;
    private final Object monitor = new Object();
    private State state = State.DOWN;

    private enum State {
        UP,
        STOPPING,
        DOWN,
        DEAD,
    }

    public Resource() { /*nothing*/ }

    public final boolean isStateUp() {
        return state == State.UP;
    }

    public final boolean isStateStopping() {
        return state == State.STOPPING;
    }

    public final boolean isStateDown() {
        return state == State.DOWN;
    }

    public final boolean isStateDead() {
        return state == State.DEAD;
    }

    public final void doAcquire() throws IllegalStateException {
        synchronized(monitor) {
            if (state != State.UP) {
                throw new IllegalStateException("Resource may not be acquired (not up)");
            }
            count ++;
        }
    }

    public final void doRelease() throws IllegalStateException {
        synchronized(monitor) {
            if (state == State.DEAD || state == State.DOWN) {
                throw new IllegalStateException("Resource may not be released (down)");
            }
            if (count == 0) {
                throw new IllegalStateException("Resource may not be released (count is zero)");
            }
            count --;
            monitor.notify();
        }
    }

    public final void doStart(final Runnable postStartAction) throws IllegalStateException {
        synchronized(monitor) {
            if (state == State.DEAD) {
                throw new IllegalStateException("Registration has been unregistered and may not be started again");
            }
            if (state != State.DOWN) {
                throw new IllegalStateException("Resource not stopped");
            }
            // DOWN -> UP
            state = State.UP;
            count = 0;
        }
        if (postStartAction != null) {
            postStartAction.run();
        }
    }

    public final void doTerminate(final Runnable postTerminateAction) throws IllegalStateException {
        synchronized(monitor) {
            if (state == State.DEAD) {
                return;
            }
            if (state != State.DOWN) {
                throw new IllegalStateException("Resource is not down");
            }
            // DOWN -> DEAD
            state = State.DEAD;
        }
        if (postTerminateAction != null) {
            postTerminateAction.run();
        }
    }

    public final void doStop(Runnable shutdownOperation, final Runnable postShutdownAction) throws IllegalStateException {
        synchronized(monitor) {
            if (state == State.DOWN) {
                return;
            }
            if (state != State.UP) {
                throw new IllegalStateException("Resource is not up");
            }
            // UP -> STOPPING
            state = State.STOPPING;
        }
        if (shutdownOperation != null) {
            shutdownOperation.run();
        }
        // state is still STOPPING since nothing changes the state from that except this code
        boolean intr = Thread.interrupted();
        try {
            synchronized (monitor) {
                while (count > 0) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        intr = Thread.interrupted();
                    }
                }
                // STOPPING -> DOWN
                state = State.DOWN;
            }
            if (postShutdownAction != null) {
                postShutdownAction.run();
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public final void runIfUp(Runnable operation) {
        synchronized(monitor) {
            if (state != State.UP) {
                throw new IllegalStateException("Resource is not up");
            }
            operation.run();
        }
    }

    public final void runIfDown(Runnable operation) {
        synchronized(monitor) {
            if (state != State.DOWN) {
                throw new IllegalStateException("Configuration may not be changed in state " + state.toString());
            }
            operation.run();
        }
    }
}

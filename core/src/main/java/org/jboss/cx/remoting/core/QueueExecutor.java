package org.jboss.cx.remoting.core;

import java.util.concurrent.Executor;
import java.util.Queue;
import java.util.LinkedList;
import org.jboss.cx.remoting.core.util.Logger;

/**
 *
 */
public final class QueueExecutor implements Executor {
    private static final Logger log = Logger.getLogger(QueueExecutor.class);

    private final Queue<Runnable> queue = new LinkedList<Runnable>();

    private State state = State.WAITING;

    private enum State {
        RUNNING,
        WAITING,
        STOPPING,
        DOWN,
    }

    public void execute(Runnable command) {
        synchronized(queue) {
            switch (state) {
                case WAITING:
                    state = State.RUNNING;
                    queue.notify();
                    // fall thru
                case RUNNING:
                    queue.add(command);
                    break;
                default:
                    throw new IllegalStateException("Executor is no longer available");
            }
        }
    }

    public void runQueue() {
        boolean intr = Thread.interrupted();
        try {
            for (;;) {
                final State newState;
                synchronized(queue) {
                    while (state == State.WAITING) {
                        try {
                            queue.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (state == State.DOWN) {
                        throw new IllegalStateException("DOWN");
                    }
                    newState = state;
                }
                for (;;) {
                    final Runnable runnable;
                    synchronized(queue) {
                        runnable = queue.poll();
                        if (runnable == null) {
                            break;
                        }
                    }
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        log.trace(t, "Error occurred while processing run queue");
                    }
                }
                if (newState == State.STOPPING) {
                    synchronized(queue) {
                        state = State.DOWN;
                        return;
                    }
                }
            }
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void shutdown() {
        synchronized(queue) {
            switch (state) {
                case WAITING:
                    queue.notify();
                case RUNNING:
                    state = State.STOPPING;
            }
        }
    }
}

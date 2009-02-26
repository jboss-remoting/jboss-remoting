package org.jboss.remoting.core;

import java.util.LinkedList;
import java.util.Queue;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.log.Logger;

/**
 * An executor designed to run all submitted tasks in the current thread.  The queue is run continuously
 * until the {@code close()} method is invoked.  Jobs may be submitted to the queue from any thread.
 * Only one thread should invoke the {@code runQueue()} method, which will run until the executor is
 * shut down.
 */
public final class QueueExecutor implements CloseableExecutor {
    private static final Logger log = org.jboss.xnio.log.Logger.getLogger(QueueExecutor.class);

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
                case STOPPING:
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
                            intr = true;
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

    public void close() {
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

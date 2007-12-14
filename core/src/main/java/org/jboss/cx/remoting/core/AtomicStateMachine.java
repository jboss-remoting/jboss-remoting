package org.jboss.cx.remoting.core;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.Callable;

/**
 *
 */
public final class AtomicStateMachine<T extends Enum<T>> {
    /* protected by {@code this} */
    private T state;

    public static <T extends Enum<T>> AtomicStateMachine<T> start(final T initialState) {
        return new AtomicStateMachine<T>(initialState);
    }

    public AtomicStateMachine(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        this.state = state;
    }

    public synchronized boolean transition(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        if (this.state == state) {
            return false;
        }
        this.state = state;
        notifyAll();
        return true;
    }

    public synchronized boolean transition(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        if (state != fromState) {
            return false;
        }
        state = toState;
        notifyAll();
        return true;
    }

    public void requireTransition(final T state) {
        if (! transition(state)) {
            throw new IllegalStateException("Already in state " + state);
        }
    }

    public void requireTransition(final T fromState, final T toState) {
        if (! transition(fromState, toState)) {
            throw new IllegalStateException("Cannot transition from " + fromState + " to " + toState + " (current state is " + state + ")");
        }
    }

    public synchronized void waitFor(final T state) throws InterruptedException {
        while (this.state != state) {
            wait();
        }
    }

    public synchronized void waitUninterruptiplyForAny() {
        waitUninterruptiblyForNot(state);
    }

    public synchronized boolean waitFor(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        while (this.state != state) {
            final long now = System.currentTimeMillis();
            if (now >= endTime) {
                return false;
            }
            wait(endTime - now);
        }
        return true;
    }

    public synchronized T waitForNot(final T state) throws InterruptedException {
        while (this.state == state) {
            wait();
        }
        return this.state;
    }

    public synchronized T waitUninterruptiblyForNot(final T state) {
        boolean intr = Thread.interrupted();
        try {
            while (this.state == state) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
            return this.state;
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized T waitForNot(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        while (this.state == state) {
            final long now = System.currentTimeMillis();
            if (now >= endTime) {
                break;
            }
            wait(endTime - now);
        }
        return this.state;
    }

    public synchronized T waitUninterruptiblyForNot(final T state, final long timeout, final TimeUnit timeUnit) {
        boolean intr = Thread.interrupted();
        try {
            final long timeoutMillis = timeUnit.toMillis(timeout);
            final long startTime = System.currentTimeMillis();
            final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
            while (this.state == state) {
                final long now = System.currentTimeMillis();
                if (now >= endTime) {
                    break;
                }
                try {
                    wait(endTime - now);
                } catch (InterruptedException e) {
                    intr = true;
                }
            }
            return this.state;
        } finally {
            if (intr) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public synchronized boolean doIf(final Runnable task, final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        if (task == null) {
            throw new NullPointerException("task is null");
        }
        if (this.state == state) {
            task.run();
            return true;
        } else {
            return false;
        }
    }

    public synchronized T getState() {
        return state;
    }

    public synchronized boolean in(T... states) {
        if (states == null) {
            throw new NullPointerException("states is null");
        }
        for (T state : states) {
            if (this.state == state) {
                return true;
            }
        }
        return false;
    }

    public synchronized void require(T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        if (this.state != state) {
            throw new IllegalStateException("Invalid state (expected " + state + ", but current state is " + this.state + ")");
        }
    }

    public synchronized String toString() {
        return "State = " + state;
    }
}

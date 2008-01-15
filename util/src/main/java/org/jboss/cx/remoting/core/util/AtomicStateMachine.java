package org.jboss.cx.remoting.core.util;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 *
 */
public final class AtomicStateMachine<T extends Enum<T>> {
    // protected by {@code lock}
    private T state;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private final Condition cond = writeLock.newCondition();

    public static <T extends Enum<T>> AtomicStateMachine<T> start(final T initialState) {
        return new AtomicStateMachine<T>(initialState);
    }

    private AtomicStateMachine(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        this.state = state;
    }

    public boolean transition(final T state) {
        writeLock.lock();
        try {
            if (state == null) {
                throw new NullPointerException("state is null");
            }
            if (this.state == state) {
                return false;
            }
            this.state = state;
            cond.signalAll();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Transition the state, and hold it at the given state until {@link #release()} is called.  Must not be
     * called if the state is already held from this thread.
     *
     * Example:
     * <pre>
     *     if (state.transitionHold(State.STOPPING)) try {
     *         // do stuff
     *     } finally {
     *         state.release();
     *     }
     * </pre>
     *
     * @param state the target state
     * @return {@code true} if the transition happened
     */
    public boolean transitionHold(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        writeLock.lock();
        try {
            if (this.state == state) {
                return false;
            }
            this.state = state;
            cond.signalAll();
            readLock.lock();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Release a held state.  Must be called from the same thread that is holding the state.
     */
    public void release() {
        readLock.unlock();
    }

    public void releaseExclusive() {
        writeLock.unlock();
    }

    public void releaseDowngrade() {
        readLock.lock();
        writeLock.unlock();
    }

    public boolean transition(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        writeLock.lock();
        try {
            if (state != fromState) {
                return false;
            }
            state = toState;
            cond.signalAll();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean transitionHold(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        writeLock.lock();
        try {
            if (state != fromState) {
                return false;
            }
            state = toState;
            cond.signalAll();
            readLock.lock();
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public boolean transitionExclusive(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        writeLock.lock();
        boolean ok = false;
        try {
            if (state != fromState) {
                writeLock.unlock();
                return false;
            }
            state = toState;
            cond.signalAll();
            ok = true;
            return true;
        } finally {
            if (! ok) {
                writeLock.unlock();
            }
        }
    }

    public void requireTransition(final T state) {
        if (! transition(state)) {
            throw new IllegalStateException("Already in state " + state);
        }
    }

    public void requireTransitionHold(final T state) {
        if (! transitionHold(state)) {
            throw new IllegalStateException("Already in state " + state);
        }
    }

    public void requireTransition(final T fromState, final T toState) {
        if (! transition(fromState, toState)) {
            throw new IllegalStateException("Cannot transition from " + fromState + " to " + toState + " (current state is " + state + ")");
        }
    }

    public void requireTransitionHold(final T fromState, final T toState) {
        if (! transitionHold(fromState, toState)) {
            throw new IllegalStateException("Cannot transition from " + fromState + " to " + toState + " (current state is " + state + ")");
        }
    }

    public void requireTransitionExclusive(T fromState, T toState) {
        if (! transitionExclusive(fromState, toState)) {
            throw new IllegalStateException("Cannot transition from " + fromState + " to " + toState + " (current state is " + state + ")");
        }
    }


    public void waitInterruptablyFor(final T state) throws InterruptedException {
        writeLock.lockInterruptibly();
        try {
            while (this.state != state) {
                cond.await();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void waitFor(final T state) {
        writeLock.lock();
        try {
            while (this.state != state) {
                cond.awaitUninterruptibly();
            }
        } finally {
            writeLock.unlock();
        }
    }

    public void waitForHold(final T state) {
        writeLock.lock();
        try {
            while (this.state != state) {
                cond.awaitUninterruptibly();
            }
            readLock.lock();
        } finally {
            writeLock.unlock();
        }
    }

    public void waitForAny() {
        writeLock.lock();
        try {
            waitForNot(state);
        } finally {
            writeLock.unlock();
        }
    }

    public boolean waitInterruptablyFor(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        final Date deadline = new Date(endTime);
        writeLock.lockInterruptibly();
        try {
            while (this.state != state) {
                if (! cond.awaitUntil(deadline)) {
                    return false;
                }
            }
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitInterruptablyForNot(final T state) throws InterruptedException {
        writeLock.lockInterruptibly();
        try {
            while (this.state == state) {
                cond.await();
            }
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitInterruptablyForNotHold(final T state) throws InterruptedException {
        writeLock.lockInterruptibly();
        try {
            while (this.state == state) {
                cond.await();
            }
            readLock.lockInterruptibly();
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitForNot(final T state) {
        writeLock.lock();
        try {
            while (this.state == state) {
                cond.awaitUninterruptibly();
            }
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitForNotHold(final T state) {
        writeLock.lock();
        try {
            while (this.state == state) {
                cond.awaitUninterruptibly();
            }
            readLock.lock();
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitForNotExclusive(final T state) {
        writeLock.lock();
        while (this.state == state) {
            cond.awaitUninterruptibly();
        }
        return this.state;
    }

    public T waitInterruptablyForNot(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        final Date deadLine = new Date(endTime);
        writeLock.lockInterruptibly();
        try {
            while (this.state == state) {
                cond.awaitUntil(deadLine);
            }
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }


    public T waitInterruptablyForNotHold(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        final Date deadLine = new Date(endTime);
        writeLock.lockInterruptibly();
        try {
            while (this.state == state) {
                cond.awaitUntil(deadLine);
            }
            readLock.lockInterruptibly();
            return this.state;
        } finally {
            writeLock.unlock();
        }
    }

    public T waitForNot(final T state, final long timeout, final TimeUnit timeUnit) {
        final long timeoutMillis = timeUnit.toMillis(timeout);
        final long startTime = System.currentTimeMillis();
        final long endTime = startTime + timeoutMillis < 0 ? Long.MAX_VALUE : startTime + timeoutMillis;
        final Date deadLine = new Date(endTime);
        boolean intr = false;
        writeLock.lock();
        try {
            while (this.state == state) {
                try {
                    if (! cond.awaitUntil(deadLine)) {
                        break;
                    }
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

    public T getState() {
        readLock.lock();
        try {
            return state;
        } finally {
            readLock.unlock();
        }
    }

    public T getStateHold() {
        readLock.lock();
        return state;
    }

    public T getStateExclusive() {
        writeLock.lock();
        return state;
    }

    public boolean inHoldExclusive(T... states) {
        if (states == null) {
            throw new NullPointerException("states is null");
        }
        writeLock.lock();
        for (T state : states) {
            if (this.state == state) {
                return true;
            }
        }
        writeLock.unlock();
        return false;
    }

    public boolean inHold(T... states) {
        if (states == null) {
            throw new NullPointerException("states is null");
        }
        readLock.lock();
        for (T state : states) {
            if (this.state == state) {
                return true;
            }
        }
        readLock.unlock();
        return false;
    }

    public boolean in(T... states) {
        if (states == null) {
            throw new NullPointerException("states is null");
        }
        readLock.lock();
        try {
            for (T state : states) {
                if (this.state == state) {
                    return true;
                }
            }
            return false;
        } finally {
            readLock.unlock();
        }
    }

    public void require(T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        readLock.lock();
        try {
            if (this.state != state) {
                throw new IllegalStateException("Invalid state (expected " + state + ", but current state is " + this.state + ")");
            }
        } finally {
            readLock.unlock();
        }
    }

    public void requireHold(T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        boolean ok = false;
        readLock.lock();
        try {
            if (this.state != state) {
                throw new IllegalStateException("Invalid state (expected " + state + ", but current state is " + this.state + ")");
            }
            ok = true;
        } finally {
            if (! ok) readLock.unlock();
        }
    }

    public void requireExclusive(T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        boolean ok = false;
        writeLock.lock();
        try {
            if (this.state != state) {
                throw new IllegalStateException("Invalid state (expected " + state + ", but current state is " + this.state + ")");
            }
            ok = true;
        } finally {
            if (! ok) writeLock.unlock();
        }
    }

    public String toString() {
        readLock.lock();
        try {
            return "State = " + state;
        } finally {
            readLock.unlock();
        }
    }
}

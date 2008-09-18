package org.jboss.remoting.util;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public final class AtomicStateMachine<T extends Enum<T> & State<T>> {
    // protected by {@code lock}
    private T state;

    private final StateLock stateLock = new StateLock();

    public static <T extends Enum<T> & State<T>> AtomicStateMachine<T> start(final T initialState) {
        return new AtomicStateMachine<T>(initialState);
    }

    private AtomicStateMachine(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        this.state = state;
    }

    public boolean transition(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockExclusive();
        try {
            if (this.state == state) {
                return false;
            }
            this.state = state;
            return true;
        } finally {
            stateLock.unlockExclusive();
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
        stateLock.lockExclusive();
        try {
            if (this.state == state) {
                return false;
            }
            this.state = state;
            stateLock.lockShared();
            return true;
        } finally {
            stateLock.unlockExclusive();
        }
    }

    public boolean transitionExclusive(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockExclusive();
        if (this.state == state) {
            return false;
        }
        this.state = state;
        return true;
    }

    /**
     * Release a held state.  Must be called from the same thread that is holding the state.
     */
    public void release() {
        stateLock.unlockShared();
    }

    public void releaseExclusive() {
        stateLock.unlockExclusive();
    }

    public void releaseDowngrade() {
        stateLock.lockShared();
        stateLock.unlockExclusive();
    }

    public boolean transition(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        stateLock.lockExclusive();
        try {
            if (state != fromState) {
                return false;
            }
            state = toState;
            return true;
        } finally {
            stateLock.unlockExclusive();
        }
    }

    public boolean transitionHold(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        stateLock.lockExclusive();
        try {
            if (state != fromState) {
                return false;
            }
            state = toState;
            stateLock.lockShared();
            return true;
        } finally {
            stateLock.unlockExclusive();
        }
    }

    public boolean transitionExclusive(final T fromState, final T toState) {
        if (fromState == null) {
            throw new NullPointerException("fromState is null");
        }
        if (toState == null) {
            throw new NullPointerException("toState is null");
        }
        stateLock.lockExclusive();
        boolean ok = false;
        try {
            if (state != fromState) {
                return false;
            }
            state = toState;
            ok = true;
            return true;
        } finally {
            if (! ok) {
                stateLock.unlockExclusive();
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

    public void waitInterruptiblyFor(final T state) throws InterruptedException {
        stateLock.lockShared();
        while (this.state != state) {
            if (this.state.isReachable(state)) {
                stateLock.yieldShared();
            } else try {
                throw new IllegalStateException("Destination state " + state + " is unreachable from " + this.state);
            } finally {
                stateLock.unlockShared();
            }
        }
        stateLock.unlockShared();
        return;
    }

    public void waitFor(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockShared();
        while (this.state != state) {
            if (this.state.isReachable(state)) {
                stateLock.yieldShared();
            } else try {
                throw new IllegalStateException("Destination state " + state + " is unreachable from " + this.state);
            } finally {
                stateLock.unlockShared();
            }
        }
        stateLock.unlockShared();
        return;
    }

    public void waitForHold(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockShared();
        while (this.state != state) {
            if (this.state.isReachable(state)) {
                stateLock.yieldShared();
            } else try {
                throw new IllegalStateException("Destination state " + state + " is unreachable from " + this.state);
            } finally {
                stateLock.unlockShared();
            }
        }
        return;
    }

    public T waitInterruptiblyForNotHold(final T state) throws InterruptedException {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockShared();
        while (this.state == state) {
            stateLock.yieldShared();
        }
        return this.state;
    }

    public T waitForNot(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockShared();
        while (this.state == state) {
            stateLock.yieldShared();
        }
        try {
            return this.state;
        } finally {
            stateLock.unlockShared();
        }
    }

    public T waitForNotHold(final T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        stateLock.lockShared();
        while (this.state == state) {
            stateLock.yieldShared();
        }
        return this.state;
    }

    public T waitInterruptiblyForNotHold(final T state, final long timeout, final TimeUnit timeUnit) throws InterruptedException {
        throw new RuntimeException("TODO - Implement");
    }

    public T waitForNotHold(final T state, final long timeout, final TimeUnit timeUnit) {
        throw new RuntimeException("TODO - Implement");
    }

    public T getState() {
        stateLock.lockShared();
        try {
            return state;
        } finally {
            stateLock.unlockShared();
        }
    }

    public T getStateHold() {
        stateLock.lockShared();
        return state;
    }

    public T getStateExclusive() {
        stateLock.lockExclusive();
        return state;
    }

    public boolean inHold(T state) {
        stateLock.lockShared();
        boolean ok = false;
        try {
            ok = this.state == state;
            return ok;
        } finally {
            if (! ok) {
                stateLock.unlockShared();
            }
        }
    }

    public boolean in(T state) {
        stateLock.lockShared();
        try {
            return this.state == state;
        } finally {
            stateLock.unlockShared();
        }
    }

    public boolean in(T... states) {
        if (states == null) {
            throw new NullPointerException("states is null");
        }
        stateLock.lockShared();
        try {
            for (T state : states) {
                if (this.state == state) {
                    return true;
                }
            }
            return false;
        } finally {
            stateLock.unlockShared();
        }
    }

    public void requireHold(T state) {
        if (state == null) {
            throw new NullPointerException("state is null");
        }
        boolean ok = false;
        stateLock.lockShared();
        try {
            if (this.state != state) {
                throw new IllegalStateException("Invalid state (expected " + state + ", but current state is " + this.state + ")");
            }
            ok = true;
        } finally {
            if (! ok) stateLock.unlockShared();
        }
    }

    public String toString() {
        stateLock.lockShared();
        try {
            return "State = " + state;
        } finally {
            stateLock.unlockShared();
        }
    }
}

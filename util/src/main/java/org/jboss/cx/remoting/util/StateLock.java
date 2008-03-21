package org.jboss.cx.remoting.util;

/**
 * Lock rules:
 *
 * Shared acquire:
 *    - Unlocked
 *    or
 *    - Shared-locked
 *
 * Exclusive acquire:
 *    - Unlocked
 *    or
 *    - All previously waiting readers have had a chance to lock
 */
public final class StateLock {
    private static final class ReaderToken {
        private int count;

        private ReaderToken(final int count) {
            this.count = count;
        }
    }

    private static final String LOCK_NOT_HELD = "Unlock when lock isn't held";

    private final Object lock = new Object();
    /**
     * A counter for the readers that will be granted after the next exclusive lock is released.
     *
     * @protectedby {@code lock}
     */
    private ReaderToken nextReaderToken = new ReaderToken(0);
    /**
     * A counter for the readers that must be granted before an exclusive lock can be granted.
     *
     * @protectedby {@code lock}
     */
    private ReaderToken currentReaderToken = new ReaderToken(0);

    // @protectedby {@code lock} (writes only)
    private volatile int sharedHolderCount = 0;
    // @protectedby {@code lock} (writes only)
    private volatile boolean exclusive = false;

    private final ThreadLocal<LockState> localLockState = new ThreadLocal<LockState>();

    private void incLocalExclCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null) {
            localLockState.set(new LockState(1, 0));
        } else {
            lockState.exclLevel++;
        }
    }

    private boolean decLocalExclCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null || lockState.exclLevel == 0) {
            throw new IllegalMonitorStateException(LOCK_NOT_HELD);
        }
        return --lockState.exclLevel == 0;
    }

    private int getLocalExclCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null) {
            return 0;
        } else {
            return lockState.exclLevel;
        }
    }

    private void incLocalShrdCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null) {
            localLockState.set(new LockState(0, 1));
        } else {
            lockState.shrdLevel++;
        }
    }

    private boolean decLocalShrdCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null || lockState.shrdLevel == 0) {
            throw new IllegalMonitorStateException(LOCK_NOT_HELD);
        }
        return --lockState.shrdLevel == 0;
    }

    private int getLocalShrdCount() {
        final LockState lockState = localLockState.get();
        if (lockState == null) {
            return 0;
        } else {
            return lockState.shrdLevel;
        }
    }

    public void lockExclusive() {
        if (getLocalExclCount() > 0) {
            incLocalExclCount();
            return;
        }
        if (getLocalShrdCount() > 0) {
            throw new IllegalMonitorStateException("Lock exclusive while shared lock is held");
        }
        synchronized (lock) {
            boolean intr = false;
            try {
                while (exclusive || currentReaderToken.count > 0 || sharedHolderCount > 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                exclusive = true;
                incLocalExclCount();
                return;
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }
    }

    public void unlockExclusive() {
        if (! exclusive) {
            throw new IllegalMonitorStateException(LOCK_NOT_HELD);
        }
        if (decLocalExclCount()) {
            synchronized (lock) {
                exclusive = false;
                currentReaderToken = nextReaderToken;
                nextReaderToken = new ReaderToken(0);
                lock.notifyAll();
            }
        }
    }

    public void lockShared() {
        if (getLocalShrdCount() > 0) {
            incLocalShrdCount();
            return;
        }
        if (getLocalExclCount() > 0) {
            sharedHolderCount++;
            incLocalShrdCount();
            return;
        }
        synchronized (lock) {
            boolean intr = false;
            try {
                final ReaderToken token = currentReaderToken;
                if (exclusive) {
                    token.count++;
                    while (exclusive) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                    token.count--;
                }
                sharedHolderCount++;
                incLocalShrdCount();
                return;
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }
    }

    public void unlockShared() {
        if (decLocalShrdCount()) {
            synchronized (lock) {
                if (--sharedHolderCount == 0) {
                    lock.notifyAll();
                }
            }
        }
    }

    public void yieldShared() {
        if (getLocalShrdCount() == 1 && getLocalExclCount() == 0) {
            synchronized (lock) {
                boolean intr = false;
                try {
                    final ReaderToken token = nextReaderToken;
                    token.count++;
                    sharedHolderCount--;
                    while (! exclusive) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                    while (exclusive) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            intr = true;
                        }
                    }
                    token.count--;
                    sharedHolderCount++;
                } finally {
                    if (intr) Thread.currentThread().interrupt();
                }
            }
        } else {
            throw new IllegalMonitorStateException("May only hold one shared lock to invoke yieldShared()");
        }
    }

    public void awaitExclusive() {
        if (getLocalExclCount() == 0) {
            throw new IllegalMonitorStateException("await() called when lock not held");
        }
        synchronized (lock) {
            boolean intr = false;
            try {
                exclusive = false;
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    intr = true;
                }
                while (exclusive || currentReaderToken.count > 0 || sharedHolderCount > 0) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        intr = true;
                    }
                }
                exclusive = true;
                incLocalExclCount();
                return;
            } finally {
                if (intr) Thread.currentThread().interrupt();
            }
        }
    }

    public void signal() {
        synchronized (lock) {
            lock.notifyAll();
        }
    }

    private static final class LockState {
        private int exclLevel;
        private int shrdLevel;

        private LockState(final int exclLevel, final int shrdLevel) {
            this.exclLevel = exclLevel;
            this.shrdLevel = shrdLevel;
        }
    }
}

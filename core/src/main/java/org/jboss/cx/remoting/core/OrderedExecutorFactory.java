package org.jboss.cx.remoting.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 *
 */
public final class OrderedExecutorFactory {
    private final Executor parent;
    private final Set<ChildExecutor> runningChildren = Collections.synchronizedSet(new HashSet<ChildExecutor>());

    public OrderedExecutorFactory(final Executor parent) {
        this.parent = parent;
    }

    public Executor getOrderedExecutor() {
        return new ChildExecutor();
    }

    private final class ChildExecutor implements Executor, Runnable {
        private final LinkedList<Runnable> tasks = new LinkedList<Runnable>();

        public void execute(Runnable command) {
            synchronized(tasks) {
                tasks.add(command);
                if (tasks.size() == 1 && runningChildren.add(this)) {
                    parent.execute(this);
                }
            }
        }

        public void run() {
            for (;;) {
                final Runnable task;
                synchronized(tasks) {
                    task = tasks.poll();
                    if (task == null) {
                        runningChildren.remove(this);
                        return;
                    }
                }
                task.run();
            }
        }
    }
}

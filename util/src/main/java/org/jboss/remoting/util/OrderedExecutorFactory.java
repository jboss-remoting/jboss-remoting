package org.jboss.remoting.util;

import java.util.concurrent.Executor;

/**
 * A factory for producing executors that run all tasks in order, which delegate to a single common executor instance.
 */
public final class OrderedExecutorFactory {
    private final Executor parent;

    /**
     * Construct a new instance delegating to the given parent executor.
     *
     * @param parent the parent executor
     */
    public OrderedExecutorFactory(final Executor parent) {
        this.parent = parent;
    }

    /**
     * Get an executor that always executes tasks in order.
     *
     * @return an ordered executor
     */
    public Executor getOrderedExecutor() {
        return new OrderedExecutor(parent);
    }
}

package org.jboss.cx.remoting.core;

import java.util.concurrent.Callable;

/**
 *
 */
public interface TaskCallable<T> extends Callable<T> {
    void cancel();
}

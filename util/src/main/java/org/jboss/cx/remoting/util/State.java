package org.jboss.cx.remoting.util;

/**
 *
 */
public interface State<T extends Enum<T> & State<T>> {
    boolean isReachable(T dest);
}

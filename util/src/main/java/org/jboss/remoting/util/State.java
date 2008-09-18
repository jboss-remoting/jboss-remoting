package org.jboss.remoting.util;

/**
 *
 */
public interface State<T extends Enum<T> & State<T>> {
    boolean isReachable(T dest);
}

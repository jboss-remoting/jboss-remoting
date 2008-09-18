package org.jboss.remoting.util;

/**
 *
 */
public interface Translator<I, O> {
    O translate(I input);
}

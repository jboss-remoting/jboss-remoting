package org.jboss.cx.remoting.util;

/**
 *
 */
public interface Translator<I, O> {
    O translate(I input);
}

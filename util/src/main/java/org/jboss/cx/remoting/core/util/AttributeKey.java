package org.jboss.cx.remoting.core.util;

/**
 *
 */
public final class AttributeKey<T> {
    private final String name;

    public AttributeKey(final String name) {
        this.name = name;
    }

    public String toString() {
        return "Key \"" + name + "\"";
    }
}

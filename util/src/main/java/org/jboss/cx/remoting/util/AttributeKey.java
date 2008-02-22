package org.jboss.cx.remoting.util;

/**
 *
 */
public final class AttributeKey<T> {
    private final String name;

    public static <T> AttributeKey<T> key(String name) {
        return new AttributeKey<T>(name);
    }

    public AttributeKey(final String name) {
        this.name = name;
    }

    public String toString() {
        return "Key \"" + name + "\"";
    }
}

package org.jboss.cx.remoting.spi;

import java.io.Serializable;
import java.util.Comparator;

/**
 *
 */
public final class InterceptorSpec implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int slot;
    private final Class<?> interceptorClass;
    private final Type type;

    public enum Type {
        PRIVATE,
        OPTIONAL,
        REQUIRED
    }

    public InterceptorSpec(final int slot, final Class<?> interceptorClass, final Type type) {
        if (type == null) throw new NullPointerException("'type' parameter is null");
        if (interceptorClass == null) throw new NullPointerException("'interceptorClass' parameter is null");
        if (slot < 0) throw new IllegalArgumentException("'slot' parameter must not be negative");
        this.slot = slot;
        this.interceptorClass = interceptorClass;
        this.type = type;
    }

    public int getSlot() {
        return slot;
    }

    public Class<?> getInterceptorClass() {
        return interceptorClass;
    }

    public Type getType() {
        return type;
    }

    private transient int hashCode;
    private transient boolean hashCodeDone;

    public int hashCode() {
        if (! hashCodeDone) {
            hashCode = slot ^ 37 * interceptorClass.hashCode() ^ 97 * type.hashCode();
            hashCodeDone = true;
        }
        return hashCode;
    }

    public boolean equals(Object obj) {
        if (! (obj instanceof InterceptorSpec)) {
            return false;
        }
        InterceptorSpec other = (InterceptorSpec) obj;
        return other.slot == slot && other.interceptorClass.equals(interceptorClass) && other.type == type;
    }

    public static final class ComparatorImpl implements Comparator<InterceptorSpec>, Serializable {
        private static final long serialVersionUID = 1L;

        private ComparatorImpl() {
        }

        public int compare(final InterceptorSpec o1, final InterceptorSpec o2) {
            return o2.slot - o1.slot;
        }
    }

    private static final Comparator<InterceptorSpec> comparator = new ComparatorImpl();

    public static Comparator<InterceptorSpec> getComparator() {
        return comparator;
    }
}

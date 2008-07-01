package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.util.List;
import java.util.Collections;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 * An object resolver that resolves objects by running through a fixed series of nested resolvers.
 */
public final class CompositeObjectResolver implements ObjectResolver {

    private static final long serialVersionUID = -5506005026832413276L;

    private final List<ObjectResolver> resolvers;

    /**
     * Construct a new composite resolver.  The given list is copied.
     *
     * @param resolvers the resolver list
     */
    public CompositeObjectResolver(List<ObjectResolver> resolvers) {
        this.resolvers = Collections.unmodifiableList(CollectionUtil.arrayList(resolvers));
    }

    /**
     * {@inheritDoc}  This implementation runs through the series of nested resovlers and calls the {@code writeReplace}
     * method on each.
     */
    public Object writeReplace(Object object) throws IOException {
        for (ObjectResolver resolver : resolvers) {
            object = resolver.writeReplace(object);
        }
        return object;
    }

    /**
     * {@inheritDoc}  This implementation runs through the series of nested resovlers and calls the {@code readResolve}
     * method on each.
     */
    public Object readResolve(Object object) throws IOException {
        for (ObjectResolver resolver : CollectionUtil.reverse(resolvers)) {
            object = resolver.readResolve(object);
        }
        return object;
    }
}

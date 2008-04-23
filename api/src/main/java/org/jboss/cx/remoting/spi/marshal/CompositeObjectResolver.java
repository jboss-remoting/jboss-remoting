package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.util.List;
import java.util.Collections;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class CompositeObjectResolver implements ObjectResolver {

    private static final long serialVersionUID = -5506005026832413276L;

    private final List<ObjectResolver> resolvers;

    public CompositeObjectResolver(List<ObjectResolver> resolvers) {
        this.resolvers = Collections.unmodifiableList(CollectionUtil.arrayList(resolvers));
    }

    public Object writeReplace(Object object) throws IOException {
        for (ObjectResolver resolver : resolvers) {
            object = resolver.writeReplace(object);
        }
        return object;
    }

    public Object readResolve(Object object) throws IOException {
        for (ObjectResolver resolver : CollectionUtil.reverse(resolvers)) {
            object = resolver.readResolve(object);
        }
        return object;
    }
}

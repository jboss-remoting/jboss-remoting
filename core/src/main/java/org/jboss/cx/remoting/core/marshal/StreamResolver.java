package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.core.StreamMarker;
import org.jboss.cx.remoting.core.util.OrderedExecutorFactory;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;

/**
 *
 */
public final class StreamResolver implements ObjectResolver {

    private static final long serialVersionUID = -5060456855964622071L;

    private final Executor streamExecutor;

    public StreamResolver(OrderedExecutorFactory factory) {
        streamExecutor = factory.getOrderedExecutor();
    }

    public StreamResolver(final Executor streamExecutor) {
        this.streamExecutor = streamExecutor;
    }

    public Object writeReplace(final Object original) throws IOException {
        // todo - run thru stream detector(s)?
        return null;
    }

    public Object readResolve(final Object original) throws IOException {
        if (original instanceof StreamMarker) {
            StreamMarker streamMarker = (StreamMarker) original;
            return null;
        } else {
            return original;
        }
    }
}

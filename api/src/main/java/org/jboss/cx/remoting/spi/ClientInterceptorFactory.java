package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;

/**
 *
 */
public interface ClientInterceptorFactory {
    ClientInterceptor createInstance(Context<?, ?> context, ContextIdentifier contextIdentifier);
}

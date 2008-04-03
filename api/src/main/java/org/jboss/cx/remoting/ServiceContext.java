package org.jboss.cx.remoting;

import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public interface ServiceContext extends Closeable<ServiceContext> {
    ConcurrentMap<Object, Object> getAttributes();
}

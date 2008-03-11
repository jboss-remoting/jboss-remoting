package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;

/**
 *
 */
public interface ObjectResolver {
    Object readResolve(Object original) throws IOException;

    Object writeReplace(Object original) throws IOException;
}

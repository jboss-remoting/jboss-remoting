package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;

/**
 *
 */
public interface ObjectResolver extends Serializable {
    Object readResolve(Object original) throws IOException;

    Object writeReplace(Object original) throws IOException;
}

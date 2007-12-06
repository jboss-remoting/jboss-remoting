package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 *
 */
public interface WritableObject {
    void writeObjectData(ObjectOutputStream oos) throws IOException;
}

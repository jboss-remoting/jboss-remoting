package org.jboss.cx.remoting.core.util;

import java.io.ObjectInput;
import java.io.IOException;

/**
 *
 */
public interface MessageInput extends ByteInput, ObjectInput {
    Object readObject() throws ClassNotFoundException, IOException;

    Object readObject(ClassLoader loader) throws ClassNotFoundException, IOException;
}

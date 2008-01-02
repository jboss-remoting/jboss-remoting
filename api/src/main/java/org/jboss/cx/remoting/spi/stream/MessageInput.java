package org.jboss.cx.remoting.spi.stream;

import java.io.Closeable;
import java.io.DataInput;
import java.io.ObjectInput;

/**
 *
 */
public interface MessageInput extends Closeable, DataInput, ObjectInput {
    int remaining();
}

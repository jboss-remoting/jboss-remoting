package org.jboss.cx.remoting.spi.marshal;

import java.io.IOException;
import java.io.Serializable;

/**
 * A resolver for marshallers.  Instances of this interface are used to dynamically substitute marker objects for
 * objects that behave specially, such as streams.  Instances of this interface are used by multiple threads and may
 * be reused any number of times.
 * <p/>
 * All instances of this interface that are associated with a session will be called in sequence, each instance getting
 * the return value of the previous instance.
 */
public interface ObjectResolver extends Serializable {

    /**
     * Substitute a real object with an object for the stream.
     *
     * @param original the real object
     * @return the object for the stream
     * @throws IOException if an error occurs
     */
    Object writeReplace(Object original) throws IOException;

    /**
     * Substitute an object from the stream with the real object.
     *
     * @param original the object from the stream
     * @return the real object
     * @throws IOException if an error occurs
     */
    Object readResolve(Object original) throws IOException;
}

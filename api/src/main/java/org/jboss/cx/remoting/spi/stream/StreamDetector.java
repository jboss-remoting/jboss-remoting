package org.jboss.cx.remoting.spi.stream;

/**
 * A class that can detect whether an object is streamable.
 */
public interface StreamDetector {
    /**
     * Detect a stream type.  If the candidate is a stream type recognized by this detector,
     * return the factory to use.
     *
     * @param candidate the candidate object
     * @return the stream serializer factory, or {@code null} if this object is not recognized
     */
    StreamSerializerFactory detectStream(Object candidate); 
}

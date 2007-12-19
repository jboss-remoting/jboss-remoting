package org.jboss.cx.remoting;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * A message that can pass between associated sessions.
 * <p/>
 * Message instances may only be used with the session that created them.  The session methods must be used to pass
 * messages to the associated session(s).
 * <p/>
 * <a name="message_body"><!-- --></a><h4>Message Body</h4>The message body is a serializable object that is sent as the
 * message payload.  Any field of the message body object, or any field of any object contained therein, may be of a
 * type recognized as a stream.  If so, the value will be replaced with a proxy that executes the streaming across the
 * wire transparently, possibly buffering the stream data.
 * <p/>
 * The message body may or may not be copied when an operation is invoked.  Therefore, the message body should not be
 * modified after a message is sent via a {@code Session}; otherwise, the result is undefined.
 */
public interface BasicMessage<T> {

    /**
     * Get the body of this message.
     *
     * @return the message body
     */
    T getBody();

    /**
     * Get the message map for this message.  This data is local to this side of the session and is not transmitted. The
     * message map may be used to hold metadata about the underlying protocol.
     *
     * @return the message map
     */
    ConcurrentMap<Object, Object> getAttributes();

    /**
     * Add a header to this message.  The treatment of headers is protocol-specific.  User code generally should not
     * directly interfere with protocol headers.
     *
     * Protocols will guarantee that the headers will be reconstructed in the same order on the remote endpoint.  The
     * protocol may or may not map headers to a protocol-specific header field.  It is solely the responsibilty of the
     * protocol handler to transport these headers.
     *
     * @param name the name of the header
     * @param value the value of the header
     */
    void addHeader(String name, String value);

    /**
     * Remove all the headers from a message with the given name.
     *
     * @param name the name of the header
     * @return the number of headers removed
     */
    int removeHeaders(String name);

    /**
     * Get the value of the header with the given name.  If more than one value exists for the given header name,
     * return the first one.
     *
     * @param name the name of the header
     * @return the value of the header, or {@code null} if the given header does not exist
     */
    String getHeaderValue(String name);

    /**
     * Get the values of the header with the given name.
     *
     * @param name the name of the header
     * @return the values of the header
     */
    Iterable<String> getHeaderValues(String name);

    /**
     * Get all of the headers present on the message, in order (if possible).  Some protocols might change the
     * order of the parameters, but for any given single parameter name, all of the corresponding values should be
     * in the same relative order as when they were added.
     *
     * @return the header names
     */
    Collection<Header> getHeaders();
}

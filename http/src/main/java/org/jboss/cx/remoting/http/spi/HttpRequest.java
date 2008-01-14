package org.jboss.cx.remoting.http.spi;

import org.jboss.cx.remoting.Header;
import java.util.List;
import java.net.URI;

/**
 *
 */
public interface HttpRequest {
    List<Header> getAllHeaders();

    List<Header> getHeaders(String name);

    Iterable<String> getHeaderNames();

    String getCharacterEncoding();

    String getContentLength();

    URI getRequestUri();

    /**
     * Get request method.  E.g. GET, POST, PUT.
     *
     * @return the request method
     */
    String getMethod();

    String getUserName();
}

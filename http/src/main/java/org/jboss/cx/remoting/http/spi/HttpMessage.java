package org.jboss.cx.remoting.http.spi;

import org.jboss.cx.remoting.Header;

/**
 *
 */
public interface HttpMessage {
    void addHeader(String name, String value);

    Iterable<Header> getHeaders();

    Iterable<String> getHeaderValues(String name);

    String getFirstHeaderValue(String name);

    Iterable<String> getHeaderNames();
}

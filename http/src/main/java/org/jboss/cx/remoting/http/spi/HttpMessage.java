package org.jboss.cx.remoting.http.spi;

/**
 *
 */
public interface HttpMessage {
    void addHeader(String name, String value);

    Iterable<String> getHeaderValues(String name);

    String getFirstHeaderValue(String name);

    Iterable<String> getHeaderNames();
}

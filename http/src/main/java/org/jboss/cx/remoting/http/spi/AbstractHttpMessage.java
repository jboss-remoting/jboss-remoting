package org.jboss.cx.remoting.http.spi;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import org.jboss.cx.remoting.core.util.CollectionUtil;

/**
 *
 */
public abstract class AbstractHttpMessage implements HttpMessage {
    private final Map<String, List<String>> headerMap = CollectionUtil.hashMap();

    public void addHeader(String name, String value) {
        final List<String> list;
        if (headerMap.containsKey(name)) {
            list = headerMap.get(name);
        } else {
            list = CollectionUtil.arrayList();
            headerMap.put(name, list);
        }
        list.add(value);
    }

    public Iterable<String> getHeaderValues(String name) {
        final List<String> list = headerMap.get(name);
        return CollectionUtil.protectedIterable(list == null ? Collections.<String>emptyList() : list);
    }

    public String getFirstHeaderValue(String name) {
        final List<String> list = headerMap.get(name);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public Iterable<String> getHeaderNames() {
        return CollectionUtil.protectedIterable(headerMap.keySet());
    }
}

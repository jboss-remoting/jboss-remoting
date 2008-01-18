package org.jboss.cx.remoting.http.spi;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Collections;
import org.jboss.cx.remoting.Header;
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

    public Iterable<Header> getHeaders() {
        return new Iterable<Header>() {
            public Iterator<Header> iterator() {
                final Iterator<Map.Entry<String, List<String>>> i = headerMap.entrySet().iterator();
                return new Iterator<Header>() {
                    private String name;
                    private Iterator<String> ii;

                    public boolean hasNext() {
                        while (ii == null || ! ii.hasNext()) {
                            if (!i.hasNext()) {
                                return false;
                            }
                            final Map.Entry<String, List<String>> entry = i.next();
                            name = entry.getKey();
                            ii = entry.getValue().iterator();
                        }
                        return true;
                    }

                    public Header next() {
                        if (! hasNext()) {
                            throw new NoSuchElementException("next() past end");
                        }
                        return new Header() {
                            public String getName() {
                                return name;
                            }

                            public String getValue() {
                                return ii.next();
                            }
                        };
                    }

                    public void remove() {
                        ii.remove();
                    }
                };
            }
        };
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

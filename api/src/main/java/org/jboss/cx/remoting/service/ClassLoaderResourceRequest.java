package org.jboss.cx.remoting.service;

import java.io.Serializable;

/**
 *
 */
public final class ClassLoaderResourceRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    public ClassLoaderResourceRequest() {
    }

    public ClassLoaderResourceRequest(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}

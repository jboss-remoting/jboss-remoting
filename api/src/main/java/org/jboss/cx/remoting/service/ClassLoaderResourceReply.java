package org.jboss.cx.remoting.service;

import java.io.Serializable;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public final class ClassLoaderResourceReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private ObjectSource<RemoteResource> resources;

    public ClassLoaderResourceReply() {
    }

    public ObjectSource<RemoteResource> getResources() {
        return resources;
    }

    public void setResources(final ObjectSource<RemoteResource> resources) {
        this.resources = resources;
    }
}

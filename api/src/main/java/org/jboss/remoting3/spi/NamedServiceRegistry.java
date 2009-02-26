/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3.spi;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jboss.remoting3.CloseHandler;
import org.jboss.remoting3.QualifiedName;
import org.jboss.remoting3.ServiceRegistrationException;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 * A registry associating names with services.  Specifically, the name is associated with a handle to a request handler
 * source instance; this handle is owned by the registry, so closing the handle will remove the entry.
 */
public final class NamedServiceRegistry {
    private static final Logger log = Logger.getLogger("org.jboss.remoting.named-registry");

    private final ConcurrentMap<QualifiedName, Handle<RequestHandlerSource>> map = new ConcurrentHashMap<QualifiedName, Handle<RequestHandlerSource>>();

    /**
     * Construct a new empty registry.
     */
    public NamedServiceRegistry() {
    }

    /**
     * Register a service at the given path.  If the given service is closed, an exception will be thrown.  Returns
     * a handle to the service which may be used to unregister this service from the registry.  In addition, if the
     * service is closed, the registration will be automatically removed.  To monitor the registration, add a close
     * handler to the returned handle.
     *
     * @param path the path of the service registration
     * @param service the service
     * @return a handle which can be used to unregister the service
     * @throws IOException if an error occurs
     */
    public Handle<RequestHandlerSource> registerService(final QualifiedName path, final RequestHandlerSource service) throws IOException {
        if (path == null) {
            throw new NullPointerException("path is null");
        }
        if (service == null) {
            throw new NullPointerException("service is null");
        }
        final Handle<RequestHandlerSource> handle = service.getHandle();
        boolean ok = false;
        try {
            final Handle<RequestHandlerSource> oldHandle = map.putIfAbsent(path, handle);
            if (oldHandle != null) {
                throw new ServiceRegistrationException(String.format("Failed to register a service at path \"%s\" on %s (a service is already registered at that location)", path, this));
            }
            handle.addCloseHandler(new CloseHandler<Handle<RequestHandlerSource>>() {
                public void handleClose(final Handle<RequestHandlerSource> closed) {
                    if (map.remove(path, service)) {
                        log.trace("Removed service %s at path \"%s\" on %s (service handle was closed)", service, path, this);
                    }
                }
            });
            log.trace("Registered %s at path \"%s\" on %s", service, path, this);
            ok = true;
            return handle;
        } finally {
            if (! ok) IoUtils.safeClose(handle);
        }
    }

    /**
     * Find a service at a location in the registry.
     *
     * @param path the path
     * @return a handle to the service, or {@code null} if it is not found
     */
    public Handle<RequestHandlerSource> lookupService(QualifiedName path) {
        return map.get(path);
    }

    /**
     * Get an unmodifiable view of the entry set of the registry.
     *
     * @return a set view
     */
    public Set<Map.Entry<QualifiedName, Handle<RequestHandlerSource>>> getEntrySet() {
        return Collections.unmodifiableSet(map.entrySet());
    }

    /**
     * Returns a brief description of this object.
     */
    public String toString() {
        return "named service registry <" + Integer.toHexString(hashCode()) + ">";
    }
}

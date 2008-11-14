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

package org.jboss.remoting.spi;

import java.util.concurrent.ConcurrentMap;
import java.io.IOException;
import org.jboss.remoting.util.QualifiedName;
import org.jboss.remoting.util.CollectionUtil;
import org.jboss.remoting.ServiceRegistrationException;
import org.jboss.remoting.CloseHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class NamedServiceRegistry {
    public static final Logger log = Logger.getLogger("org.jboss.remoting.named-registry");

    private final ConcurrentMap<QualifiedName, Handle<RequestHandlerSource>> map = CollectionUtil.concurrentMap();

    public NamedServiceRegistry() {
    }

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

    public Handle<RequestHandlerSource> lookupService(QualifiedName path) {
        return map.get(path);
    }

    public String toString() {
        return "named service registry <" + Integer.toHexString(hashCode()) + ">";
    }
}

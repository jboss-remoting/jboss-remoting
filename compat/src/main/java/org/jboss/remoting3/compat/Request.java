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

package org.jboss.remoting3.compat;

import java.util.Map;

/**
 * A request wrapper object, used to issue a Remoting 2.x invocation with a metadata map.
 */
public final class Request<I> {
    private I body;
    private Map<Object, Object> metadata;

    public Request() {
    }

    public Request(final I body, final Map<Object, Object> metadata) {
        this.body = body;
        this.metadata = metadata;
    }

    public I getBody() {
        return body;
    }

    public void setBody(final I body) {
        this.body = body;
    }

    public Map<Object, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(final Map<Object, Object> metadata) {
        this.metadata = metadata;
    }
}

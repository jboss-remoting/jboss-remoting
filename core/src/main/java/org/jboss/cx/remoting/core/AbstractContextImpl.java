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

package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.spi.AbstractHandleableCloseable;

/**
 *
 */
public abstract class AbstractContextImpl<T> extends AbstractHandleableCloseable<T> {

    private final ConcurrentMap<Object, Object> attributes = CollectionUtil.concurrentMap();

    protected AbstractContextImpl(final Executor executor) {
        super(executor);
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return attributes;
    }

    protected Executor getExecutor() {
        return super.getExecutor();
    }

    protected boolean isOpen() {
        return super.isOpen();
    }

    public String toString() {
        return "generic context instance <" + Integer.toString(hashCode()) + ">";
    }
}

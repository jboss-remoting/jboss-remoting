/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.service.locator;

import java.io.Serializable;

public final class ServiceReply<I, O> implements Serializable {

    private static final long serialVersionUID = 5722718939504235368L;

    private final int slot;
    private final Class<I> actualRequestClass;
    private final Class<O> actualReplyClass;

    public ServiceReply(final int slot, final Class<I> actualRequestClass, final Class<O> actualReplyClass) {
        this.slot = slot;
        this.actualRequestClass = actualRequestClass;
        this.actualReplyClass = actualReplyClass;
    }

    public static <I, O> ServiceReply<I, O> create(final int slot, final Class<I> actualRequestClass, final Class<O> actualReplyClass) {
        return new ServiceReply<I,O>(slot, actualRequestClass, actualReplyClass);
    }

    public int getSlot() {
        return slot;
    }

    public Class<I> getActualRequestClass() {
        return actualRequestClass;
    }

    public Class<O> getActualReplyClass() {
        return actualReplyClass;
    }
}

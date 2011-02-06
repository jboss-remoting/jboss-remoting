/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.rpc;

/**
 * A simple RPC request.  The reply type is a simple non-generic (raw) type.
 *
 * @param <I> the request type
 * @param <O> the reply type
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class SimpleRPCRequest<I, O> implements RPCRequest<I, O> {

    private static final long serialVersionUID = -7238787527077529142L;

    private final I request;
    private final Class<O> replyType;

    /**
     * Construct a new instance.
     *
     * @param request the request object
     * @param replyType the class of the reply type
     */
    public SimpleRPCRequest(final I request, final Class<O> replyType) {
        this.request = request;
        this.replyType = replyType;
    }

    /** {@inheritDoc} */
    public I getRequestObject() {
        return request;
    }

    /** {@inheritDoc} */
    public O cast(final Object object) throws ClassCastException {
        return replyType.cast(object);
    }
}

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

import java.io.Serializable;

/**
 * A remote procedure call request object.
 *
 * @param <I> the request type
 * @param <O> the reply type for this request object
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface RPCRequest<I, O> extends Serializable {

    /**
     * Get the request object for this request.
     *
     * @return the request object
     */
    I getRequestObject();

    /**
     * Cast the given object to the correct reply type.
     *
     * @param object the object to cast
     * @return the properly-typed object
     * @throws ClassCastException if the cast failed
     */
    O cast(Object object) throws ClassCastException;
}

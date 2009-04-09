/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3;

/**
 * A simple request listener implementation that implements all methods with no-operation implementations.
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public abstract class AbstractRequestListener<I, O> implements RequestListener<I, O> {

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientOpen(final ClientContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceOpen(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleServiceClose(final ServiceContext context) {
    }

    /**
     * {@inheritDoc}  This implementation performs no operation.
     */
    public void handleClientClose(final ClientContext context) {
    }
}

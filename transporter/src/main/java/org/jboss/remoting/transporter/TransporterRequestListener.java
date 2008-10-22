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

package org.jboss.remoting.transporter;

import org.jboss.remoting.RequestContext;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.AbstractRequestListener;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.io.IOException;

/**
 *
 */
public final class TransporterRequestListener<T> extends AbstractRequestListener<TransporterInvocation,Object> {
    private final T target;

    public TransporterRequestListener(final T target) {
        this.target = target;
    }

    public void handleRequest(final RequestContext<Object> context, final TransporterInvocation request) throws RemoteExecutionException {
        try {
            final Method method = target.getClass().getMethod(request.getName(), request.getParameterTypes());
            method.invoke(target, request.getArgs());
        } catch (NoSuchMethodException e) {
            doSendFailure(context, new NoSuchMethodError("No such method on the remote side: " + e.getMessage()));
        } catch (InvocationTargetException e) {
            doSendFailure(context, e.getCause());
        } catch (IllegalAccessException e) {
            doSendFailure(context, new IllegalAccessError("Illegal access in remote method invocation: " + e.getMessage()));
        }
    }

    private void doSendFailure(final RequestContext<Object> context, final Throwable throwable) {
        try {
            context.sendFailure(null, throwable);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3.samples.socket;

import java.io.IOException;

import org.jboss.remoting3.spi.RemoteRequestHandler;
import org.jboss.xnio.Result;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 24, 2009
 * </p>
 */
public class RequestHandlerFuture implements Result<RemoteRequestHandler> {
   private RemoteRequestHandler requestHandler;
   private IOException exception;

   @Override
   public boolean setCancelled() {return true;}

   @Override
   public boolean setException(IOException exception) {
      this.exception = exception;
      return true;
   }

   @Override
   public boolean setResult(RemoteRequestHandler result) {
      this.requestHandler = result;
      return true;
   }

   public RemoteRequestHandler get() {
      return requestHandler;
   }
   
   public IOException getException() {
      return exception;
   }
}

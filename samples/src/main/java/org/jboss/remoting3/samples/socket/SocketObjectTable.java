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
import java.io.Serializable;
import java.util.concurrent.Executor;

import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.RequestListener;
import org.jboss.remoting3.samples.socket.server.SocketClientListener;
import org.jboss.xnio.log.Logger;

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Nov 9, 2009
 * </p>
 */
public class SocketObjectTable<I, O> implements org.jboss.marshalling.ObjectTable {
   private static final Logger log = Logger.getLogger(SocketObjectTable.class);
	private static final EndpointToken ENDPOINT_TOKEN = new EndpointToken();
	private static final ExecutorToken EXECUTOR_TOKEN = new ExecutorToken();
	   
	private Endpoint endpoint;
	private Executor executor;
	private SocketObjectTableWriter socketObjectTableWriter;
	
	public SocketObjectTable(Endpoint endpoint, Executor executor) {
	   this.endpoint = endpoint;
	   this.executor = executor;
	   socketObjectTableWriter = new SocketObjectTableWriter();
	}
	
	@Override
	public Writer getObjectWriter(Object object) throws IOException {
		if (object instanceof Endpoint || object instanceof Executor) {
		   return socketObjectTableWriter;
		}
		return null;
	}

	@SuppressWarnings("unchecked")
   @Override
	public Object readObject(Unmarshaller unmarshaller) throws IOException, ClassNotFoundException {
		Object o = unmarshaller.readObject();
		if (o instanceof EndpointToken) {
		   return endpoint;
		}
		if (o instanceof ExecutorToken) {
		   return executor;
		}
		return o;
	}
	
	
   private static class SocketObjectTableWriter implements Writer {
      public void writeObject(Marshaller marshaller, Object object) throws IOException {
         if (object instanceof Endpoint) {
            log.info(this + " got Endpoint: " + object);
            marshaller.writeObject(ENDPOINT_TOKEN);
         }
         else if (object instanceof Executor) {
            log.info(this + " got Executor: " + object);
            marshaller.writeObject(EXECUTOR_TOKEN);            
         }
         else {
            throw new RuntimeException("expecting Endpoint or Executor");
         }
      }
   }
   
   
   private static class EndpointToken implements Serializable {
      private static final long serialVersionUID = -7307241847641193094L;
   }
   
   private static class ExecutorToken implements Serializable {
      private static final long serialVersionUID = -8687614439586428163L;
   }
}

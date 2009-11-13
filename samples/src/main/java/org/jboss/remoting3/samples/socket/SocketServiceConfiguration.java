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

/**
 * @author <a href="ron.sigal@jboss.com">Ron Sigal</a>
 * @version 
 * <p>
 * Copyright Oct 16, 2009
 * </p>
 */
public class SocketServiceConfiguration<I, O> {
   private String serviceType;
   private String groupName;
   private Class<I> requestClass;
   private Class<O> responseClass;
   private String host;
   private int port;

   public SocketServiceConfiguration(String serviceType, String groupName, Class<I> requestClass, Class<O> responseClass, String host, int port) {
      this.serviceType = serviceType;
      this.groupName = groupName;
      this.requestClass = requestClass;
      this.responseClass = responseClass;
      this.host = host;
      this.port = port;
   }
   
   public String getServiceType() {
      return serviceType;
   }
   
   public void setServiceType(String serviceType) {
      this.serviceType = serviceType;
   }
   
   public String getGroupName() {
      return groupName;
   }
   
   public void setGroupName(String groupName) {
      this.groupName = groupName;
   }

   public Class<I> getRequestClass() {
      return requestClass;
   }

   public void setRequestClass(Class<I> requestClass) {
      this.requestClass = requestClass;
   }

   public Class<O> getResponseClass() {
      return responseClass;
   }

   public void setResponseClass(Class<O> responseClass) {
      this.responseClass = responseClass;
   }

   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }
   
   public String toString() {
      return "[serviceType=" + serviceType + ", groupName=" + groupName + "requestClass=" + requestClass + ", responseClass= " + responseClass + ", host=" + host + ", port=" + port + "]";
   }
}


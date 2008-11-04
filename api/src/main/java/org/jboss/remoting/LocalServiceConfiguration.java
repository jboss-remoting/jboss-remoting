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

package org.jboss.remoting;

/**
 * A configuration for a service to be deployed into the endpoint.
 */
public final class LocalServiceConfiguration<I, O> {
    private final RequestListener<I, O> requestListener;
    private final Class<I> requestClass;
    private final Class<O> replyClass;
    private String serviceType;
    private String groupName;
    private int metric;

    public LocalServiceConfiguration(final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) {
        this.requestListener = requestListener;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
    }

    public RequestListener<I, O> getRequestListener() {
        return requestListener;
    }

    public Class<I> getRequestClass() {
        return requestClass;
    }

    public Class<O> getReplyClass() {
        return replyClass;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(final String groupName) {
        this.groupName = groupName;
    }

    public int getMetric() {
        return metric;
    }

    public void setMetric(final int metric) {
        this.metric = metric;
    }
}

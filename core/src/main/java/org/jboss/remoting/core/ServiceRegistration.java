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

package org.jboss.remoting.core;

import org.jboss.remoting.spi.remote.RequestHandlerSource;
import org.jboss.remoting.SimpleCloseable;

/**
 *
 */
public final class ServiceRegistration {
    private final boolean remote;
    private final String serviceType;
    private final String groupName;
    private final String endpointName;
    private final int metric;
    private final RequestHandlerSource handlerSource;
    private volatile SimpleCloseable handle;

    ServiceRegistration(final String serviceType, final String groupName, final String endpointName, final int metric, final RequestHandlerSource handlerSource) {
        remote = true;
        this.serviceType = serviceType;
        this.groupName = groupName;
        this.endpointName = endpointName;
        this.metric = metric;
        this.handlerSource = handlerSource;
    }

    ServiceRegistration(final String serviceType, final String groupName, final String endpointName, final RequestHandlerSource handlerSource) {
        remote = false;
        metric = 0;
        this.serviceType = serviceType;
        this.groupName = groupName;
        this.endpointName = endpointName;
        this.handlerSource = handlerSource;
    }

    public boolean matches(final String serviceType, final String groupName, final String endpointName) {
        return  (serviceType == null || serviceType.length() == 0 || serviceType.equals(this.serviceType)) &&
                (groupName == null || groupName.length() == 0 || groupName.equals(this.groupName)) &&
                (endpointName == null || endpointName.length() == 0 || endpointName.equals(this.endpointName));
    }

    public boolean isRemote() {
        return remote;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getEndpointName() {
        return endpointName;
    }

    public int getMetric() {
        return metric;
    }

    public RequestHandlerSource getHandlerSource() {
        return handlerSource;
    }

    public SimpleCloseable getHandle() {
        return handle;
    }

    void setHandle(final SimpleCloseable handle) {
        this.handle = handle;
    }
}

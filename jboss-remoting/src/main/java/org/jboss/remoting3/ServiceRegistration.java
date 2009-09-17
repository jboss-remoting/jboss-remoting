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

package org.jboss.remoting3;

import org.jboss.remoting3.spi.RequestHandlerConnector;
import org.jboss.xnio.OptionMap;

/**
 *
 */
final class ServiceRegistration {
    private final boolean remote;
    private final String serviceType;
    private final String groupName;
    private final String endpointName;
    private final OptionMap optionMap;
    private final RequestHandlerConnector connector;
    private volatile SimpleCloseable handle;

    ServiceRegistration(final String serviceType, final String groupName, final String endpointName, final OptionMap optionMap, final RequestHandlerConnector connector) {
        remote = true;
        this.serviceType = serviceType;
        this.groupName = groupName;
        this.endpointName = endpointName;
        this.optionMap = optionMap;
        this.connector = connector;
    }

    ServiceRegistration(final String serviceType, final String groupName, final String endpointName, final RequestHandlerConnector connector) {
        remote = false;
        optionMap = OptionMap.EMPTY;
        this.serviceType = serviceType;
        this.groupName = groupName;
        this.endpointName = endpointName;
        this.connector = connector;
    }

    public boolean matches(final String serviceType, final String groupName, final String endpointName) {
        return  (serviceType == null || serviceType.length() == 0 || "*".equals(serviceType) || serviceType.equals(this.serviceType)) &&
                (groupName == null || groupName.length() == 0 || "*".equals(groupName) || groupName.equals(this.groupName)) &&
                (endpointName == null || endpointName.length() == 0 || "*".equals(endpointName) || endpointName.equals(this.endpointName));
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

    public OptionMap getOptionMap() {
        return optionMap;
    }

    public RequestHandlerConnector getConnector() {
        return connector;
    }

    public SimpleCloseable getHandle() {
        return handle;
    }

    void setHandle(final SimpleCloseable handle) {
        this.handle = handle;
    }
}

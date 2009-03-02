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

import org.jboss.remoting3.spi.RequestHandlerSource;

/**
 * A configuration for registering a remote service with an endpoint.
 *
 * @apiviz.exclude
 */
public final class RemoteServiceConfiguration {
    private String serviceType;
    private String groupName;
    private String endpointName;
    private RequestHandlerSource requestHandlerSource;
    private int metric;

    /**
     * Construct a new instance.
     */
    public RemoteServiceConfiguration() {
    }

    /**
     * Get the service type.
     *
     * @return the service type
     */
    public String getServiceType() {
        return serviceType;
    }

    /**
     * Set the service type.
     *
     * @param serviceType the service type
     */
    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
    }

    /**
     * Get the service group name.
     *
     * @return the group name
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Set the service group name.
     *
     * @param groupName the group name
     */
    public void setGroupName(final String groupName) {
        this.groupName = groupName;
    }

    /**
     * Get the remote endpoint name.
     *
     * @return the remote endpoint name
     */
    public String getEndpointName() {
        return endpointName;
    }

    /**
     * Set the remote endpoint name.
     *
     * @param endpointName the remote endpoint name
     */
    public void setEndpointName(final String endpointName) {
        this.endpointName = endpointName;
    }

    /**
     * Get the request handler source of the remote service.
     *
     * @return the request handler source
     */
    public RequestHandlerSource getRequestHandlerSource() {
        return requestHandlerSource;
    }

    /**
     * Set the request handler source of the remote service.
     *
     * @param requestHandlerSource the request handler source
     */
    public void setRequestHandlerSource(final RequestHandlerSource requestHandlerSource) {
        this.requestHandlerSource = requestHandlerSource;
    }

    /**
     * Get the metric of the remote service.
     *
     * @return the metric
     */
    public int getMetric() {
        return metric;
    }

    /**
     * Set the metric of the remote service.
     *
     * @param metric the metric
     */
    public void setMetric(final int metric) {
        this.metric = metric;
    }
}

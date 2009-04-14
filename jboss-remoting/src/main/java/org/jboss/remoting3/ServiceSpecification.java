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

import java.net.URI;

/**
 * A specification of a JBoss Remoting service.
 *
 * @apiviz.exclude
 */
public final class ServiceSpecification {
    private final String serviceType;
    private final String groupName;
    private final String endpointName;

    public ServiceSpecification(final String serviceType, final String groupName, final String endpointName) {
        if (serviceType == null) {
            throw new NullPointerException("serviceType is null");
        }
        if (groupName == null) {
            throw new NullPointerException("groupName is null");
        }
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        if (serviceType.length() > 0 && ! "*".equals(serviceType)) {
            ServiceURI.validateServiceType(serviceType);
            this.serviceType = serviceType.toLowerCase();
        } else {
            this.serviceType = "*";
        }
        if (groupName.length() > 0 && ! "*".equals(groupName)) {
            ServiceURI.validateGroupName(groupName);
            this.groupName = groupName.toLowerCase();
        } else {
            this.groupName = "*";
        }
        if (endpointName.length() > 0 && ! "*".equals(endpointName)) {
            ServiceURI.validateEndpointName(endpointName);
            this.endpointName = endpointName.toLowerCase();
        } else {
            this.endpointName = "*";
        }
    }

    public static ServiceSpecification fromUri(final URI uri) {
        return new ServiceSpecification(ServiceURI.getServiceType(uri), ServiceURI.getGroupName(uri), ServiceURI.getEndpointName(uri));
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

    public boolean matches(final URI serviceUri) {
        if (! ServiceURI.isRemotingServiceUri(serviceUri)) {
            return false;
        }
        if (! "*".equals(serviceType)) {
            if (! serviceType.equals(ServiceURI.getServiceType(serviceUri))) {
                return false;
            }
        }
        if (! "*".equals(groupName)) {
            if (! groupName.equals(ServiceURI.getGroupName(serviceUri))) {
                return false;
            }
        }
        if (! "*".equals(endpointName)) {
            if (! endpointName.equals(ServiceURI.getEndpointName(serviceUri))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(final Object o) {
        if (this == o) return true;
        if (! (o instanceof ServiceSpecification)) return false;
        final ServiceSpecification that = (ServiceSpecification) o;
        if (!endpointName.equals(that.endpointName)) return false;
        if (!groupName.equals(that.groupName)) return false;
        if (!serviceType.equals(that.serviceType)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = serviceType.hashCode();
        result = 31 * result + groupName.hashCode();
        result = 31 * result + endpointName.hashCode();
        return result;
    }
}

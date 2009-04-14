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
 * A listener for watching service registrations on an endpoint.
 *
 * @apiviz.landmark
 */
public interface ServiceRegistrationListener {

    /**
     * Receive notification that a service was registered.
     *
     * @param listenerHandle the handle to this listener
     * @param info the servce information
     */
    void serviceRegistered(SimpleCloseable listenerHandle, ServiceInfo info);

    /**
     * Information about a registered service.
     *
     * @apiviz.exclude
     */
    final class ServiceInfo {
        private String serviceType;
        private String groupName;
        private int metric;
        private RequestHandlerSource requestHandlerSource;
        private SimpleCloseable registrationHandle;

        /**
         * Construct a new instance.
         */
        public ServiceInfo() {
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
         * Get the group name.
         *
         * @return the group name
         */
        public String getGroupName() {
            return groupName;
        }

        /**
         * Set the group name.
         *
         * @param groupName the group name
         */
        public void setGroupName(final String groupName) {
            this.groupName = groupName;
        }

        /**
         * Get the metric.
         *
         * @return the metric
         */
        public int getMetric() {
            return metric;
        }

        /**
         * Set the metric.
         *
         * @param metric the metric
         */
        public void setMetric(final int metric) {
            this.metric = metric;
        }

        /**
         * Get the request handler source.
         *
         * @return the request handler source
         */
        public RequestHandlerSource getRequestHandlerSource() {
            return requestHandlerSource;
        }

        /**
         * Set the request handler source.
         *
         * @param requestHandlerSource the request handler source
         */
        public void setRequestHandlerSource(final RequestHandlerSource requestHandlerSource) {
            this.requestHandlerSource = requestHandlerSource;
        }

        /**
         * Get the registration handle.  Closing this handle will remove the registration.
         *
         * @return the registration handle
         */
        public SimpleCloseable getRegistrationHandle() {
            return registrationHandle;
        }

        /**
         * Set the registration handle.
         *
         * @param registrationHandle the registration handle
         */
        public void setRegistrationHandle(final SimpleCloseable registrationHandle) {
            this.registrationHandle = registrationHandle;
        }
    }
}

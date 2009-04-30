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
 * A listener for watching service location events on an endpoint.
 *
 * @apiviz.landmark
 */
public interface ServiceLocationListener {
    void serviceLocated(SimpleCloseable listenerHandler, ServiceInfo info);

    /**
     * Information about a located service.
     */
    final class ServiceInfo {
        private URI serviceUri;
        private URI locationUri;
        private int metric;

        /**
         * Get the URI of the located service.
         *
         * @return the URI
         */
        public URI getServiceUri() {
            return serviceUri;
        }

        /**
         * Set the URI of the located service.
         *
         * @param serviceUri the URI
         */
        public void setServiceUri(final URI serviceUri) {
            this.serviceUri = serviceUri;
        }

        /**
         * Get the URI of the location of the located service.
         *
         * @return the URI
         */
        public URI getLocationUri() {
            return locationUri;
        }

        /**
         * Set the URI of the location of the located service.
         *
         * @param locationUri the URI
         */
        public void setLocationUri(final URI locationUri) {
            this.locationUri = locationUri;
        }

        /**
         * Get the preference metric of this located service.
         *
         * @return the preference metric
         */
        public int getMetric() {
            return metric;
        }

        /**
         * Set the preference metric of this located service.
         *
         * @param metric the preference metric
         */
        public void setMetric(final int metric) {
            this.metric = metric;
        }
    }
}

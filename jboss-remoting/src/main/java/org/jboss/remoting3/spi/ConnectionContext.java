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

package org.jboss.remoting3.spi;

import org.jboss.xnio.OptionMap;

/**
 * The context for connections to service incoming requests to open a client service.
 */
public interface ConnectionContext {

    /**
     * Open a service.
     *
     * @param serviceType the service type
     * @param groupName the service group name
     * @param optionMap the open options
     * @param serviceResult the result of the service open
     */
    void openService(String serviceType, String groupName, OptionMap optionMap, ServiceResult serviceResult);

    /**
     * The result acceptor for a service open request.
     */
    interface ServiceResult {

        /**
         * Called if the service was opened.
         *
         * @param requestHandler the opened request handler
         * @param optionMap the service's option map
         */
        void opened(RequestHandler requestHandler, OptionMap optionMap);

        /**
         * Called if no matching service was found.
         */
        void notFound();
    }
}

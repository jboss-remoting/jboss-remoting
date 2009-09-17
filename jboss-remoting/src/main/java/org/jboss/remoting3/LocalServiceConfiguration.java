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

import org.jboss.xnio.OptionMap;

/**
 * A configuration for a service to be deployed into the endpoint.
 *
 * @apiviz.exclude
 *
 * @param <I> the request type
 * @param <O> the reply type
 */
public final class LocalServiceConfiguration<I, O> {
    private final ClientListener<I, O> clientListener;
    private final Class<I> requestClass;
    private final Class<O> replyClass;
    private String serviceType;
    private String groupName;
    private OptionMap optionMap = OptionMap.EMPTY;

    /**
     * Construct a new instance.
     *
     * @param clientListener the client listener
     * @param requestClass the request class
     * @param replyClass the reply class
     */
    public LocalServiceConfiguration(final ClientListener<I, O> clientListener, final Class<I> requestClass, final Class<O> replyClass) {
        this.clientListener = clientListener;
        this.requestClass = requestClass;
        this.replyClass = replyClass;
    }

    /**
     * Create a new instance.
     *
     * @param clientListener the client listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new configuration instance
     */
    public static <I, O> LocalServiceConfiguration<I, O> create(final ClientListener<I, O> clientListener, final Class<I> requestClass, final Class<O> replyClass) {
        return new LocalServiceConfiguration<I,O>(clientListener, requestClass, replyClass);
    }

    /**
     * Get the request listener for this service.
     *
     * @return the request listener
     */
    public ClientListener<I, O> getClientListener() {
        return clientListener;
    }

    /**
     * Get the request class.
     *
     * @return the request class
     */
    public Class<I> getRequestClass() {
        return requestClass;
    }

    /**
     * Get the reply class.
     *
     * @return the reply class
     */
    public Class<O> getReplyClass() {
        return replyClass;
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
     * Get the option map for the service.
     *
     * @return the option map
     */
    public OptionMap getOptionMap() {
        return optionMap;
    }

    /**
     * Set the option map for the service.
     *
     * @param optionMap the option map
     */
    public void setOptionMap(final OptionMap optionMap) {
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        this.optionMap = optionMap;
    }
}

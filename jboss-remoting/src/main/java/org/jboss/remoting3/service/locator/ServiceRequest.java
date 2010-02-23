/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.service.locator;

import java.io.Serializable;
import org.jboss.remoting3.TypedRequest;

/**
 * A request to open a {@code Client} with the given service type and optional group name.
 *
 * @param <I> the expected request type
 * @param <O> the expected reply type
 */
public final class ServiceRequest<I, O> implements Serializable, TypedRequest<ServiceRequest<I, O>, ServiceReply<I, O>> {

    private static final long serialVersionUID = -1369189420414317503L;

    private final String serviceType;
    private final String groupName;
    private final Class<I> expectedRequestClass;
    private final Class<O> expectedReplyClass;

    /**
     * Construct a new instance.
     *
     * @param serviceType the service type
     * @param groupName the optional group name ({@code null} means unspecified)
     * @param expectedRequestClass the expected request class
     * @param expectedReplyClass the expected reply class
     */
    public ServiceRequest(final String serviceType, final String groupName, final Class<I> expectedRequestClass, final Class<O> expectedReplyClass) {
        if (serviceType == null) {
            throw new IllegalArgumentException("serviceType is null");
        }
        if (expectedRequestClass == null) {
            throw new IllegalArgumentException("expectedRequestClass is null");
        }
        if (expectedReplyClass == null) {
            throw new IllegalArgumentException("expectedReplyClass is null");
        }
        this.serviceType = serviceType;
        this.groupName = groupName;
        this.expectedRequestClass = expectedRequestClass;
        this.expectedReplyClass = expectedReplyClass;
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
     * Get the group name.
     *
     * @return the group name, or {@code null} if it wasn't specified
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Get the expected request class.
     *
     * @return the expected request class
     */
    public Class<I> getExpectedRequestClass() {
        return expectedRequestClass;
    }

    /**
     * Get the expected reply class.
     *
     * @return the expected reply class
     */
    public Class<O> getExpectedReplyClass() {
        return expectedReplyClass;
    }

    /**
     * {@inheritDoc}  This implementation verifies that the request and reply classes are compatible with the
     * given reply object.
     */
    @SuppressWarnings({ "unchecked" })
    public ServiceReply<I, O> castReply(final Object reply) throws ClassCastException {
        ServiceReply<?, ?> roughReply = (ServiceReply<?, ?>) reply;
        roughReply.getActualRequestClass().asSubclass(expectedRequestClass);
        expectedReplyClass.asSubclass(roughReply.getActualReplyClass());
        return (ServiceReply<I, O>) roughReply;
    }
}

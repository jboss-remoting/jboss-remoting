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

package org.jboss.remoting3.security;

import org.wildfly.common.Assert;
import org.wildfly.security.permission.AbstractNameSetOnlyPermission;
import org.wildfly.security.util.StringEnumeration;
import org.wildfly.security.util.StringMapping;

/**
 * This class is for permissions relating to Remoting endpoints.
 */
public class RemotingPermission extends AbstractNameSetOnlyPermission<RemotingPermission> {

    private static final long serialVersionUID = 4984517897378387571L;

    private static final StringEnumeration names = StringEnumeration.of(
        "createEndpoint",
        "connect",
        "addConnectionProvider",
        "registerService",
        "getConnectionProviderInterface"
    );

    private static final StringMapping<RemotingPermission> mapping = new StringMapping<>(names, RemotingPermission::new);

    public static final RemotingPermission CREATE_ENDPOINT = mapping.getItemById(0);
    public static final RemotingPermission CONNECT = mapping.getItemById(1);
    public static final RemotingPermission ADD_CONNECTION_PROVIDER = mapping.getItemById(2);
    public static final RemotingPermission REGISTER_SERVICE = mapping.getItemById(3);
    public static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE = mapping.getItemById(4);

    public static final RemotingPermission ALL_PERMISSION = new RemotingPermission("*");

    /**
     * Creates a new {@code RemotingPermission} object with the specified name.
     * The name is the symbolic name of the {@code RemotingPermission}.
     *
     * @param name the name of the {@code RemotingPermission}
     *
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public RemotingPermission(String name) throws NullPointerException, IllegalArgumentException {
        super(name, names);
    }

    /**
     * Creates a new {@code RemotingPermission} object with the specified name.
     * The name is the symbolic name of the {@code RemotingPermission}, and the
     * actions string is currently unused.
     *
     * @param name the name of the {@code RemotingPermission}
     * @param actions ignored
     *
     * @throws NullPointerException if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is empty
     */
    public RemotingPermission(String name, @SuppressWarnings("unused") String actions) throws NullPointerException, IllegalArgumentException {
        super(name, names);
    }

    /**
     * Create a new permission which is identical to this one, except with a new {@code name}.
     *
     * @param name the name to use
     * @return the new permission (must not be {@code null})
     * @throws IllegalArgumentException if the name is not valid
     */
    public RemotingPermission withName(final String name) {
        return forName(name);
    }

    /**
     * Get the permission with the given name.
     *
     * @param name the name (must not be {@code null})
     * @return the permission (not {@code null})
     * @throws IllegalArgumentException if the name is not valid
     */
    public static RemotingPermission forName(final String name) {
        Assert.checkNotNullParam("name", name);
        return name.equals("*") ? ALL_PERMISSION : mapping.getItemByString(name);
    }
}

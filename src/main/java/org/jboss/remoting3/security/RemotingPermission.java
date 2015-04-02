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

import java.security.BasicPermission;

/**
 * This class is for permissions relating to Remoting endpoints.
 */
public class RemotingPermission extends BasicPermission {

    private static final long serialVersionUID = 4984517897378387571L;

    public static final RemotingPermission CREATE_ENDPOINT = new RemotingPermission("createEndpoint");
    public static final RemotingPermission CONNECT = new RemotingPermission("connect");
    public static final RemotingPermission ADD_CONNECTION_PROVIDER = new RemotingPermission("addConnectionProvider");
    public static final RemotingPermission REGISTER_SERVICE = new RemotingPermission("registerService");
    public static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE = new RemotingPermission("getConnectionProviderInterface");

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
        super(name);
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
    public RemotingPermission(String name, String actions) throws NullPointerException, IllegalArgumentException {
        super(name, actions);
    }

    Object readResolve() {
        switch (getName()) {
            case "createEndpoint": return CREATE_ENDPOINT;
            case "connect": return CONNECT;
            case "addConnectionProvider": return ADD_CONNECTION_PROVIDER;
            case "registerService": return REGISTER_SERVICE;
            case "getConnectionProviderInterface": return GET_CONNECTION_PROVIDER_INTERFACE;
            default: return this;
        }
    }
}

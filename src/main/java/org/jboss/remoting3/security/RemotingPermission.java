/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        "getConnectionProviderInterface",
        "getConnectionPeerIdentity"
    );

    private static final StringMapping<RemotingPermission> mapping = new StringMapping<>(names, RemotingPermission::new);

    public static final RemotingPermission CREATE_ENDPOINT = mapping.getItemById(0);
    public static final RemotingPermission CONNECT = mapping.getItemById(1);
    public static final RemotingPermission ADD_CONNECTION_PROVIDER = mapping.getItemById(2);
    public static final RemotingPermission REGISTER_SERVICE = mapping.getItemById(3);
    public static final RemotingPermission GET_CONNECTION_PROVIDER_INTERFACE = mapping.getItemById(4);
    public static final RemotingPermission GET_CONNECTION_PEER_IDENTITY = mapping.getItemById(5);

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

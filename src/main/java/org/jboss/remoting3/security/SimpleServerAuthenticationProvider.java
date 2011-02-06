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

import java.io.IOException;
import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthenticationException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;

/**
 * A server authentication handler which maintains a simple map of user names and passwords.
 */
public final class SimpleServerAuthenticationProvider implements ServerAuthenticationProvider {

    private static final RemotingPermission ADD_USER_PERM = new RemotingPermission("addServerUser");

    private final Map<String, Map<String, Entry>> map = new HashMap<String, Map<String, Entry>>();

    /** {@inheritDoc}
     * @param mechanismName*/
    public CallbackHandler getCallbackHandler(final String mechanismName) {
        return new CallbackHandler() {
            public void handle(final Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                String userName = null;
                String realmName = null;
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        final NameCallback nameCallback = (NameCallback) callback;
                        final String defaultName = nameCallback.getDefaultName();
                        userName = defaultName.toLowerCase().trim();
                        nameCallback.setName(userName);
                    } else if (callback instanceof RealmCallback) {
                        final RealmCallback realmCallback = (RealmCallback) callback;
                        final String defaultRealm = realmCallback.getDefaultText();
                        if (defaultRealm != null) {
                            realmName = defaultRealm.toLowerCase().trim();
                            realmCallback.setText(realmName);
                        }
                    } else if (callback instanceof PasswordCallback) {
                        final PasswordCallback passwordCallback = (PasswordCallback) callback;
                        // retrieve the record based on user and realm (if any)
                        Entry entry = null;
                        if (realmName == null) {
                            // scan all realms
                            synchronized (map) {
                                for (Map<String, Entry> realmMap : map.values()) {
                                    if (realmMap.containsKey(userName)) {
                                        entry = realmMap.get(userName);
                                        break;
                                    }
                                }
                            }
                        } else {
                            synchronized (map) {
                                final Map<String, Entry> realmMap = map.get(realmName);
                                if (realmMap != null) {
                                    entry = realmMap.get(userName);
                                }
                            }
                        }
                        if (entry == null) {
                            throw new AuthenticationException("No matching user found");
                        }
                        passwordCallback.setPassword(entry.getPassword());
                    } else if (callback instanceof AuthorizeCallback) {
                        final AuthorizeCallback authorizeCallback = (AuthorizeCallback) callback;
                        authorizeCallback.setAuthorized(authorizeCallback.getAuthenticationID().equals(authorizeCallback.getAuthorizationID()));
                    } else {
                        throw new UnsupportedCallbackException(callback, "Callback not supported: " + callback);
                    }
                }
            }
        };
    }

    /**
     * Add a user to the authentication table.
     *
     * @param userName the user name
     * @param userRealm the user realm
     * @param password the password
     * @param keyPairs the key pairs for this identity
     */
    public void addUser(String userName, String userRealm, char[] password, KeyPair... keyPairs) {
        if (userName == null) {
            throw new IllegalArgumentException("userName is null");
        }
        if (userRealm == null) {
            throw new IllegalArgumentException("userRealm is null");
        }
        if (password == null) {
            throw new IllegalArgumentException("password is null");
        }
        if (keyPairs == null) {
            throw new IllegalArgumentException("keyPairs is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(ADD_USER_PERM);
        }
        final String canonUserRealm = userRealm.toLowerCase().trim();
        final String canonUserName = userName.toLowerCase().trim();
        synchronized (map) {
            Map<String, Entry> realmMap = map.get(canonUserRealm);
            if (realmMap == null) {
                realmMap = new HashMap<String, Entry>();
                map.put(canonUserRealm, realmMap);
            }
            realmMap.put(canonUserName, new Entry(canonUserName, canonUserRealm, password, keyPairs));
        }
    }

    private static final class Entry {
        private final String userName;
        private final String userRealm;
        private final char[] password;
        private final KeyPair[] keyPairs;

        private Entry(final String userName, final String userRealm, final char[] password, final KeyPair[] keyPairs) {
            this.userName = userName;
            this.userRealm = userRealm;
            this.password = password;
            this.keyPairs = keyPairs;
        }

        String getUserName() {
            return userName;
        }

        String getUserRealm() {
            return userRealm;
        }

        char[] getPassword() {
            return password;
        }

        KeyPair[] getKeyPairs() {
            return keyPairs;
        }
    }
}

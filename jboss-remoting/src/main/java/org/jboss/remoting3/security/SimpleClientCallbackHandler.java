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
import org.jboss.xnio.log.Logger;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextOutputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.RealmChoiceCallback;

/**
 * A callback handler which performs client authentication steps.
 */
public class SimpleClientCallbackHandler implements CallbackHandler {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.security.client");

    private final String actualUserName;
    private final String actualUserRealm;
    private final char[] password;

    /**
     * An empty password array.
     */
    public static final char[] EMPTY_PASSWORD = new char[0];

    /**
     * Create a new instance.
     *
     * @param actualUserName the user name to supply, or {@code null} for none
     * @param actualUserRealm the user realm to supply, or {@code null} for none
     * @param password the password to supply, or {@code null} for none
     */
    public SimpleClientCallbackHandler(final String actualUserName, final String actualUserRealm, final char[] password) {
        this.actualUserName = actualUserName;
        this.actualUserRealm = actualUserRealm;
        this.password = password;
    }

    /**
     * Handle the array of given callbacks.
     *
     * @param callbacks the callbacks to handle
     * @throws UnsupportedCallbackException if a callback is unsupported
     */
    public void handle(final Callback[] callbacks) throws UnsupportedCallbackException {
        MAIN: for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                final NameCallback nameCallback = (NameCallback) callback;
                final String defaultName = nameCallback.getDefaultName();
                log.trace("User name requested; prompt '%s', default is '%s', ours is '%s'", nameCallback.getPrompt(), defaultName, actualUserName);
                if (actualUserName == null) {
                    if (defaultName != null) {
                        nameCallback.setName(defaultName);
                    }
                } else {
                    nameCallback.setName(actualUserName);
                }
            } else if (callback instanceof RealmCallback) {
                final RealmCallback realmCallback = (RealmCallback) callback;
                final String defaultRealm = realmCallback.getDefaultText();
                log.trace("Realm requested; prompt '%s', default is '%s', ours is '%s'", realmCallback.getPrompt(), defaultRealm, actualUserRealm);
                if (actualUserRealm == null) {
                    if (defaultRealm != null) {
                        realmCallback.setText(defaultRealm);
                    }
                } else {
                    realmCallback.setText(actualUserRealm);
                }
            } else if (callback instanceof RealmChoiceCallback && actualUserRealm != null) {
                final RealmChoiceCallback realmChoiceCallback = (RealmChoiceCallback) callback;
                final String[] choices = realmChoiceCallback.getChoices();
                for (int i = 0; i < choices.length; i++) {
                    if (choices[i] != null && choices[i].equals(actualUserRealm)) {
                        realmChoiceCallback.setSelectedIndex(i);
                        continue MAIN;
                    }
                }
                throw new UnsupportedCallbackException(callback, "No realm choices match realm '" + actualUserRealm + "'");
            } else if (callback instanceof TextOutputCallback) {
                final TextOutputCallback textOutputCallback = (TextOutputCallback) callback;
                final String kind;
                switch (textOutputCallback.getMessageType()) {
                    case TextOutputCallback.ERROR: kind = "ERROR"; break;
                    case TextOutputCallback.INFORMATION: kind = "INFORMATION"; break;
                    case TextOutputCallback.WARNING: kind = "WARNING"; break;
                    default: kind = "UNKNOWN"; break;
                }
                log.debug("Authentication layer produced a %s message: %s", kind, textOutputCallback.getMessage());
            } else if (callback instanceof PasswordCallback && password != null) {
                final PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(password);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}

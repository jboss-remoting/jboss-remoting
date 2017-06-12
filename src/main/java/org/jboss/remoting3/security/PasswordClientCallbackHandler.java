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

import org.jboss.logging.Logger;

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
public class PasswordClientCallbackHandler implements CallbackHandler {

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
    public PasswordClientCallbackHandler(final String actualUserName, final String actualUserRealm, final char[] password) {
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
                log.tracef("User name requested; prompt '%s', default is '%s', ours is '%s'", nameCallback.getPrompt(), defaultName, actualUserName);
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
                log.tracef("Realm requested; prompt '%s', default is '%s', ours is '%s'", realmCallback.getPrompt(), defaultRealm, actualUserRealm);
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
                log.debugf("Authentication layer produced a %s message: %s", kind, textOutputCallback.getMessage());
            } else if (callback instanceof PasswordCallback) {
                final PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(password);
            } else {
                throw new UnsupportedCallbackException(callback);
            }
        }
    }
}

/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, JBoss Inc., and individual contributors as indicated
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PublicKeySaslServer implements SaslServer {
    private int phase = 0;
    private final CallbackHandler callbackHandler;

    PublicKeySaslServer(final String mechanism, final String protocol, final String name, final Map<String, ?> props, final CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    public String getMechanismName() {
        return null;
    }

    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        switch (phase) {
            case 0: {
                // initial challenge
                try {
                    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    final RealmCallback realmCallback = new RealmCallback("Authentication Realm");
                    callbackHandler.handle(new Callback[] { realmCallback });
                    final String realm = realmCallback.getText();
                    stream.write(realm.getBytes("UTF-8"));
                    stream.write(0);

                } catch (SaslException e) {
                    throw e;
                } catch (IOException e) {
                    throw new SaslException("Failed", e);
                } catch (UnsupportedCallbackException e) {
                    throw new SaslException("Failed", e);
                }
            }
        }
        return new byte[0];
    }

    public boolean isComplete() {
        return false;
    }

    public String getAuthorizationID() {
        return null;
    }

    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        return new byte[0];
    }

    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        return new byte[0];
    }

    public Object getNegotiatedProperty(final String propName) {
        return null;
    }

    public void dispose() throws SaslException {
    }
}

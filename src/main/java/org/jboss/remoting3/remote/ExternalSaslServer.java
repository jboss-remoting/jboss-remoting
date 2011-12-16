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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

final class ExternalSaslServer implements SaslServer {
    private final AtomicBoolean complete = new AtomicBoolean();
    private String authorizationID;
    private final Principal peerPrincipal;
    private final CallbackHandler callbackHandler;
    private static final byte[] EMPTY = new byte[0];

    ExternalSaslServer(final CallbackHandler callbackHandler, final Principal peerPrincipal) {
        this.callbackHandler = callbackHandler;
        this.peerPrincipal = peerPrincipal;
    }

    public String getMechanismName() {
        return "EXTERNAL";
    }

    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        if (complete.getAndSet(true)) {
            throw new SaslException("Received response after complete");
        }
        String userName;
        try {
            userName = new String(response, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new SaslException("Cannot convert user name from UTF-8", e);
        }
        if (userName.length() == 0) {
            userName = peerPrincipal.getName();
        }
        final AuthorizeCallback authorizeCallback = new AuthorizeCallback(peerPrincipal.getName(), userName);
        handleCallback(callbackHandler, authorizeCallback);
        if (authorizeCallback.isAuthorized()) {
            authorizationID = authorizeCallback.getAuthorizedID();
        } else {
            throw new SaslException("EXTERNAL: " + peerPrincipal.getName() + " is not authorized to act as " + userName);
        }

        return EMPTY;
    }

    private static void handleCallback(CallbackHandler handler, Callback callback) throws SaslException {
        try {
            handler.handle(new Callback[] {
                    callback,
            });
        } catch (SaslException e) {
            throw e;
        } catch (IOException e) {
            throw new SaslException("Failed to authenticate due to callback exception", e);
        } catch (UnsupportedCallbackException e) {
            throw new SaslException("Failed to authenticate due to unsupported callback", e);
        }
    }

    public boolean isComplete() {
        return complete.get();
    }

    public String getAuthorizationID() {
        return authorizationID;
    }

    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        throw new IllegalStateException();
    }

    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        throw new IllegalStateException();
    }

    public Object getNegotiatedProperty(final String propName) {
        return null;
    }

    public void dispose() throws SaslException {
    }
}

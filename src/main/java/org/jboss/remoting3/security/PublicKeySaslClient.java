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
import java.security.PublicKey;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

/**
 * A client for the {@code jboss-publickey} SASL mechanism.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class PublicKeySaslClient implements SaslClient {
    private final CallbackHandler callbackHandler;
    private int phase = 0;

    /**
     * Construct a new instance.
     *
     * @param handler the callback handler
     */
    PublicKeySaslClient(final CallbackHandler handler) {
        callbackHandler = handler;
    }

    /**
     * Returns the mechanism name, {@code jboss-publickey}.
     *
     * @return the mechanism name
     */
    public String getMechanismName() {
        return "jboss-publickey";
    }

    /**
     * Returns {@code false}.
     *
     * @return {@code false}
     *
     * @see SaslClient#hasInitialResponse()
     */
    public boolean hasInitialResponse() {
        return false;
    }

    public byte[] evaluateChallenge(final byte[] challenge) throws SaslException {
        switch (phase) {
            case 0: {
                try {

                    final NameCallback nameCallback = new NameCallback("User Name");
                    final RealmCallback realmCallback = new RealmCallback("User Realm");
                    final PublicKeyCallback publicKeyCallback = new PublicKeyCallback("Public Key");
                    callbackHandler.handle(new Callback[] { nameCallback, realmCallback, publicKeyCallback });
                    final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    stream.write(nameCallback.getName().getBytes("UTF-8"));
                    stream.write(0);
                    stream.write(realmCallback.getText().getBytes("UTF-8"));
                    stream.write(0);
                    final PublicKey publicKey = publicKeyCallback.getPublicKey();
                    stream.write(publicKey.getAlgorithm().getBytes("UTF-8"));
                    stream.write(0);
                    stream.write(publicKey.getEncoded());
                    stream.write(0);
                    return stream.toByteArray();
                } catch (SaslException e) {
                    throw e;
                } catch (IOException e) {
                    throw new SaslException("Failed to process SASL challenge", e);
                } catch (UnsupportedCallbackException e) {
                    throw new SaslException("Failed to process SASL challenge", e);
                }
            }
            case 1: {
                try {

                    final PrivateKeyCallback privateKeyCallback = new PrivateKeyCallback("Private Key");
                    callbackHandler.handle(new Callback[] { privateKeyCallback });

                } catch (UnsupportedCallbackException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public boolean isComplete() {
        return false;
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

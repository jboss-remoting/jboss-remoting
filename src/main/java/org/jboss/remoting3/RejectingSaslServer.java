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

package org.jboss.remoting3;

import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

/**
 * A SASL server which rejects all authentication attempts.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RejectingSaslServer implements SaslServer {

    private static final String MECH_NAME = "<reject>";

    /**
     * Construct a new instance.
     */
    public RejectingSaslServer() {
    }

    public String getMechanismName() {
        return MECH_NAME;
    }

    public byte[] evaluateResponse(final byte[] response) throws SaslException {
        throw rejectedAuth();
    }

    public boolean isComplete() {
        return true;
    }

    public String getAuthorizationID() {
        return null;
    }

    public byte[] unwrap(final byte[] incoming, final int offset, final int len) throws SaslException {
        throw rejectedAuth();
    }

    public byte[] wrap(final byte[] outgoing, final int offset, final int len) throws SaslException {
        throw rejectedAuth();
    }

    public Object getNegotiatedProperty(final String propName) {
        return null;
    }

    public void dispose() throws SaslException {
    }

    private static SaslException rejectedAuth() {
        return new SaslException("Authentication rejected");
    }
}

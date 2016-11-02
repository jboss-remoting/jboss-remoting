/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

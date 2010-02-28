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

package org.jboss.remoting3.remote;

import java.nio.ByteBuffer;

import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

final class SaslClientContext implements SaslContext {
    private final SaslClient saslClient;

    SaslClientContext(final SaslClient saslClient) {
        this.saslClient = saslClient;
    }

    public String getMechanismName() {
        return saslClient.getMechanismName();
    }

    public Object getNegotiatedProperty(final String name) {
        return saslClient.getNegotiatedProperty(name);
    }

    public ByteBuffer unwrap(final ByteBuffer src) throws SaslException {
        final byte[] unwrapped;
        if (src.hasArray()) {
            final byte[] orig = src.array();
            final int start = src.arrayOffset() + src.position();
            final int len = src.remaining();
            unwrapped = saslClient.unwrap(orig, start, len);
        } else {
            final int len = src.remaining();
            final byte[] orig = new byte[len];
            src.get(orig);
            unwrapped = saslClient.unwrap(orig, 0, len);
        }
        return ByteBuffer.wrap(unwrapped);
    }
}

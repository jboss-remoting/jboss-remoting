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

import java.io.IOException;
import java.nio.ByteBuffer;
import org.xnio.channels.MessageHandler;

import javax.security.sasl.SaslException;

final class SaslUnwrappingMessageHandler implements MessageHandler, MessageHandler.Setter {
    private final SaslContext saslContext;
    private volatile MessageHandler delegate;

    SaslUnwrappingMessageHandler(final SaslContext saslContext, final MessageHandler delegate) {
        this.saslContext = saslContext;
        this.delegate = delegate;
    }

    public void handleMessage(final ByteBuffer buffer) {
        try {
            delegate.handleMessage(saslContext.unwrap(buffer));
        } catch (SaslException e) {
            delegate.handleException(e);
        }
    }

    public void handleEof() {
        delegate.handleEof();
    }

    public void handleException(final IOException e) {
        delegate.handleException(e);
    }

    public void set(final MessageHandler messageHandler) {
        delegate = messageHandler;
    }
}

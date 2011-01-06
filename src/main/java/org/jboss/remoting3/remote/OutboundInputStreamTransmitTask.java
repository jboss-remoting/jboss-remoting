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
import java.io.InputStream;
import java.nio.ByteBuffer;
import org.jboss.xnio.IoUtils;

final class OutboundInputStreamTransmitTask implements Runnable {
    private final InputStream inputStream;
    private final OutboundStream outboundStream;

    OutboundInputStreamTransmitTask(final InputStream inputStream, final OutboundStream outboundStream) {
        this.inputStream = inputStream;
        this.outboundStream = outboundStream;
    }

    public void run() {
        final InputStream inputStream = this.inputStream;
        try {
            final OutboundStream outboundStream = this.outboundStream;
            byte[] bytes = new byte[1024];
            for (;;) {
                int res = 0;
                try {
                    res = inputStream.read(bytes);
                } catch (IOException e) {
                    outboundStream.sendException();
                    return;
                }
                if (res == -1) {
                    outboundStream.sendEof();
                    return;
                }
                try {
                    while (res > 0) {
                        final ByteBuffer buffer = outboundStream.getBuffer();
                        final int xsz = Math.min(buffer.remaining(), res);
                        res -= xsz;
                        buffer.put(bytes, 0, xsz).flip();
                        outboundStream.send(buffer);
                    }
                } catch (IOException e) {
                    // async msg. received; stop transmitting, send close.
                    outboundStream.sendEof();
                    return;
                }
            }
        } finally {
            IoUtils.safeClose(inputStream);
        }
    }
}

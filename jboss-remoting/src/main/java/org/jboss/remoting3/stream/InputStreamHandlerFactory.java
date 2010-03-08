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

package org.jboss.remoting3.stream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import org.jboss.xnio.ChannelListener;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.log.Logger;

/**
 * A handler factory for automatic forwarding of input streams.
 */
public final class InputStreamHandlerFactory implements StreamHandlerFactory<InputStream, StreamChannel> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.stream.inputstream");

    /** {@inheritDoc} */
    public StreamHandler<InputStream, StreamChannel> createStreamHandler(final InputStream localInstance, final StreamContext streamContext) throws IOException {
        return new Handler(localInstance, streamContext);
    }

    private static class Handler implements StreamHandler<InputStream, StreamChannel> {

        private static final long serialVersionUID = 731898100063706343L;

        private final StreamContext streamContext;
        private transient final InputStream localInstance;

        private Handler(final InputStream instance, final StreamContext context) {
            localInstance = instance;
            streamContext = context;
        }

        public ChannelListener<StreamChannel> getLocalHandler() {
            return new ChannelListener<StreamChannel>() {
                public void handleEvent(final StreamChannel channel) {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            IoUtils.safeClose(localInstance);
                        }
                    });
                    streamContext.execute(new LocalRunnable(channel, localInstance));
                }
            };
        }

        public ChannelListener<Channel> getRemoteHandler() {
            return IoUtils.nullChannelListener();
        }

        public InputStream getRemoteProxy(final IoFuture<? extends StreamChannel> futureChannel) {
            return new ProxyInputStream(futureChannel);
        }
    }

    private static class ProxyInputStream extends InputStream {

        private final IoFuture<? extends StreamChannel> futureChannel;

        public ProxyInputStream(final IoFuture<? extends StreamChannel> futureChannel) {
            this.futureChannel = futureChannel;
        }

        public int read() throws IOException {
            return 0;
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            final StreamChannel channel = futureChannel.get();
            int res;
            do {
                res = channel.read(ByteBuffer.wrap(b, off, len));
                if (res == 0) {
                    channel.awaitReadable();
                }
            } while (res == 0);
            return res;
        }

        public void close() throws IOException {
            futureChannel.get().close();
        }
    }

    private static class LocalRunnable implements Runnable {

        private static final int MIN_BUFFER_SIZE = 512;
        private static final int MAX_BUFFER_SIZE = 1024;

        private final StreamChannel channel;
        private final InputStream localInstance;

        public LocalRunnable(final StreamChannel channel, final InputStream instance) {
            this.channel = channel;
            localInstance = instance;
        }

        public void run() {
            try {
                final byte[] bytes = new byte[MAX_BUFFER_SIZE];
                final ByteBuffer buf = ByteBuffer.wrap(bytes);
                for (;;) {
                    int cnt = 0, pos;
                    while ((pos = buf.position()) < MIN_BUFFER_SIZE) {
                        cnt = localInstance.read(bytes, pos, buf.remaining());
                        if (cnt == -1) {
                            break;
                        }
                    }
                    buf.flip();
                    int wcnt;
                    do {
                        wcnt = channel.write(buf);
                        if (wcnt == 0) {
                            channel.awaitWritable();
                        }
                    } while (buf.hasRemaining());
                    if (cnt == -1) {
                        channel.close();
                    }
                    buf.clear();
                }
            } catch (IOException e) {
                log.error(e, "Failed to read input stream data");
                IoUtils.safeClose(channel);
            }
        }
    }
}

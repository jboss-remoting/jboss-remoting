package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.spi.marshal.MarshallerFactory;
import org.jboss.cx.remoting.stream.ObjectSource;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.channels.StreamSourceChannel;
import org.jboss.xnio.channels.StreamSinkChannel;
import org.jboss.xnio.channels.CommonOptions;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Client;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class ObjectSourceStreamSerializerFactory implements StreamSerializerFactory {

    private static final long serialVersionUID = -7485283009011459281L;

    private static final Logger log = Logger.getLogger(ObjectSourceStreamSerializerFactory.class);


    private MarshallerFactory marshallerFactory;

    public MarshallerFactory getMarshallerFactory() {
        return marshallerFactory;
    }

    public void setMarshallerFactory(final MarshallerFactory marshallerFactory) {
        this.marshallerFactory = marshallerFactory;
    }

    public IoHandler<? super StreamChannel> getLocalSide(final Object localSide) throws IOException {
        
        return null;
    }

    public Object getRemoteSide(final Client<StreamChannel> remoteClient) throws IOException {
        return null;
    }

    public static class LocalHandler implements IoHandler<StreamSinkChannel> {
        private final ObjectSource objectSource;

        public void handleOpened(final StreamSinkChannel channel) {
            if (channel.getOptions().contains(CommonOptions.TCP_NODELAY)) try {
                channel.setOption(CommonOptions.TCP_NODELAY, Boolean.TRUE);
            } catch (Exception e) {
                log.trace("Error setting TCP_NODELAY option: %s", e.getMessage());
            }
            channel.resumeWrites();
        }

        public void handleReadable(final StreamSinkChannel channel) {
            // not invoked
        }

        public void handleWritable(final StreamSinkChannel channel) {
        }

        public void handleClosed(final StreamSinkChannel channel) {
            IoUtils.safeClose(objectSource);
        }
    }

    public static class RemoteHandler implements IoHandler<StreamSourceChannel> {

        public void handleOpened(final StreamSourceChannel channel) {
        }

        public void handleReadable(final StreamSourceChannel channel) {
        }

        public void handleWritable(final StreamSourceChannel channel) {
        }

        public void handleClosed(final StreamSourceChannel channel) {
        }
    }
}

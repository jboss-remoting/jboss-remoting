package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.ObjectMessageInput;
import org.jboss.cx.remoting.spi.ObjectMessageOutput;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;

/**
 *
 */
public final class CoreStream {
    private static final Logger log = Logger.getLogger(CoreStream.class);

    private final CoreSession coreSession;
    private final Executor executor;
    private final StreamIdentifier streamIdentifier;
    private final StreamSerializer streamSerializer;

    private final ProtocolHandler protocolHandler;

    private final StreamContext streamContext;

    /**
     * A new stream (local side).  The {@code executor} must specify an executor that is guaranteed to execute all tasks in order.
     *
     * @param coreSession the session
     * @param executor the executor to use to handle data
     * @param streamIdentifier the stream identifier
     * @param streamSerializerFactory the stream serializer
     * @param local the local side
     */
    CoreStream(final CoreSession coreSession, final Executor executor, final StreamIdentifier streamIdentifier, final StreamSerializerFactory streamSerializerFactory, final Object local) throws IOException {
        this.coreSession = coreSession;
        this.executor = executor;
        this.streamIdentifier = streamIdentifier;
        protocolHandler = coreSession.getProtocolHandler();
        streamContext = new StreamContextImpl();
        streamSerializer = streamSerializerFactory.getLocalSide(streamContext, local);
    }

    /**
     * A new stream (remote side).  The {@code executor} must specify an executor that is guaranteed to execute all tasks in order.
     *
     * @param coreSession the session
     * @param executor the executor to use to handle data
     * @param streamIdentifier the stream identifier
     * @param streamSerializerFactory the stream serializer
     */
    CoreStream(final CoreSession coreSession, final Executor executor, final StreamIdentifier streamIdentifier, final StreamSerializerFactory streamSerializerFactory) throws IOException {
        this.coreSession = coreSession;
        this.executor = executor;
        this.streamIdentifier = streamIdentifier;
        protocolHandler = coreSession.getProtocolHandler();
        streamContext = new StreamContextImpl();
        streamSerializer = streamSerializerFactory.getRemoteSide(streamContext);
    }

    public void receiveStreamData(final ObjectMessageInput data) {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    streamSerializer.handleData(data);
                } catch (Exception e) {
                    log.trace(e, "Stream failed to handle incoming data (%s)", data);
                }
            }
        });
    }

    public RemoteStreamSerializer getRemoteSerializer() {
        return (RemoteStreamSerializer) streamSerializer;
    }

    public StreamSerializer getStreamSerializer() {
        return streamSerializer;
    }

    // stream context

    private final class StreamContextImpl implements StreamContext {

        private StreamContextImpl() {
        }

        public ObjectMessageOutput writeMessage() throws IOException {
            return protocolHandler.sendStreamData(streamIdentifier, executor);
        }

        public void close() throws IOException {
            try {
                protocolHandler.closeStream(streamIdentifier);
            } finally {
                // todo clean up stream
//                coreSession.removeStream(streamIdentifier);
            }
        }
    }
}

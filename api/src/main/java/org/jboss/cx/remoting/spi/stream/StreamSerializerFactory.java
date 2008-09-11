package org.jboss.cx.remoting.spi.stream;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.ChannelSource;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import java.io.IOException;
import java.io.Serializable;

/**
 * A factory for stream serializers.  Stream serializers are responsible for forwarding streams across the network
 * in a manner specific to the stream type.
 */
public interface StreamSerializerFactory extends Serializable {

    /**
     * Get the XNIO handler for the local side of the serializer.  This side will access the local instance.  The returned
     * handler is attached to the channel that is associated with the stream instance.
     *
     * @param localSide the instance that is being serialized
     * @param streamContext the stream context
     * @return the local handler
     * @throws IOException if an error occurs while preparing the handler
     */
    IoHandler<? super AllocatedMessageChannel> getLocalSide(Object localSide, StreamContext streamContext) throws IOException;

    /**
     * Get the remote proxy instance for the remote side of the serializer.  This side will emulate the streaming object
     * on the remote side.  This method is responsible for initiating the communications channel, which the returned
     * instance will use to transmit data.
     *
     * @param channelSource the channel source which is used to create the stream's channel
     * @param streamContext the stream context
     * @return the remote proxy instance
     * @throws IOException if an error occurs while preparing the handler
     */
    Object getRemoteSide(ChannelSource<AllocatedMessageChannel> channelSource, StreamContext streamContext) throws IOException;
}

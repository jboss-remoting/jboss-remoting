package org.jboss.cx.remoting.spi.stream;

import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Client;
import org.jboss.xnio.channels.StreamChannel;
import java.io.IOException;
import java.io.Serializable;

/**
 *
 */
public interface StreamSerializerFactory extends Serializable {
    IoHandler<? super StreamChannel> getLocalSide(Object localSide) throws IOException;

    Object getRemoteSide(Client<StreamChannel> remoteClient) throws IOException;
}

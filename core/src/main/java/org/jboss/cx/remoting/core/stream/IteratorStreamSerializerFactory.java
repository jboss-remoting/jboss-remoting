package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import java.util.Iterator;
import org.jboss.cx.remoting.util.ObjectMessageInput;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.stream.ObjectSource;
import org.jboss.cx.remoting.stream.Streams;
import org.jboss.xnio.channels.StreamChannel;
import org.jboss.xnio.IoHandler;
import org.jboss.xnio.Client;

/**
 *
 */
public final class IteratorStreamSerializerFactory implements StreamSerializerFactory {

    private static final long serialVersionUID = 5106872230130868988L;

    private 

    public IoHandler<? super StreamChannel> getLocalSide(final Object localSide) throws IOException {
        return null;
    }

    public Object getRemoteSide(final Client<StreamChannel> remoteClient) throws IOException {
        return null;
    }
}

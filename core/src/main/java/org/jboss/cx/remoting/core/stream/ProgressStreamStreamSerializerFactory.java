package org.jboss.cx.remoting.core.stream;

import java.io.IOException;
import org.jboss.cx.remoting.util.MessageInput;
import org.jboss.cx.remoting.util.MessageOutput;
import org.jboss.cx.remoting.spi.stream.RemoteStreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamContext;
import org.jboss.cx.remoting.spi.stream.StreamSerializer;
import org.jboss.cx.remoting.spi.stream.StreamSerializerFactory;
import org.jboss.cx.remoting.stream.ProgressStream;

/**
 *
 */
public final class ProgressStreamStreamSerializerFactory implements StreamSerializerFactory {
    public StreamSerializer getLocalSide(StreamContext context, Object local) throws IOException {
        return new StreamSerializerImpl((ProgressStream)local);
    }

    public RemoteStreamSerializer getRemoteSide(StreamContext context) throws IOException {
        return new RemoteStreamSerializerImpl(context);
    }

    public static final class StreamSerializerImpl implements StreamSerializer {
        private final ProgressStream progressStream;

        public StreamSerializerImpl(final ProgressStream progressStream) {
            this.progressStream = progressStream;
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
            final String operationTitle = data.readUTF();
            final int unitsDone = data.readInt();
            final int totalUnits = data.readInt();
            final boolean approx = data.readBoolean();
            progressStream.update(operationTitle, unitsDone, totalUnits, approx);
        }

        public void handleClose() throws IOException {
        }
    }

    public static final class RemoteStreamSerializerImpl implements RemoteStreamSerializer {
        private final StreamContext context;

        public RemoteStreamSerializerImpl(final StreamContext context) {
            this.context = context;
        }

        public ProgressStream getRemoteInstance() {
            return new ProgressStream() {
                public void update(String operationTitle, int unitsDone, int totalUnits, boolean approx) {
                    try {
                        final MessageOutput msg = context.writeMessage();
                        msg.writeUTF(operationTitle);
                        msg.writeInt(unitsDone);
                        msg.writeInt(totalUnits);
                        msg.writeBoolean(approx);
                        msg.commit();
                    } catch (IOException e) {
                        // todo - log?
                    }
                }
            };
        }

        public void handleOpen() throws IOException {
        }

        public void handleData(MessageInput data) throws IOException {
        }

        public void handleClose() throws IOException {
        }
    }
}

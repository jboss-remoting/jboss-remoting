package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.MessageInput;
import org.jboss.cx.remoting.spi.protocol.ByteInput;
import org.jboss.serial.io.JBossObjectInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 *
 */
public final class MessageInputImpl extends JBossObjectInputStream implements MessageInput {
    private final ByteInput source;

    public MessageInputImpl(final ByteInput source) throws IOException {
        super(new InputStream() {
            public int read(byte b[]) throws IOException {
                return source.read(b);
            }

            public int read(byte b[], int off, int len) throws IOException {
                return source.read(b, off, len);
            }

            public int read() throws IOException {
                return source.read();
            }

            public void close() throws IOException {
                source.close();
            }

            public int available() throws IOException {
                return source.remaining();
            }
        });
        this.source = source;
    }

    public int remaining() {
        return source.remaining();
    }
}

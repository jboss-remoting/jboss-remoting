package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.MessageOutput;
import org.jboss.cx.remoting.spi.protocol.ByteOutput;
import org.jboss.serial.io.JBossObjectOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 *
 */
public final class MessageOutputImpl extends JBossObjectOutputStream implements MessageOutput {
    private final ByteOutput target;

    public MessageOutputImpl(final ByteOutput target) throws IOException {
        super(new OutputStream() {
            public void write(int b) throws IOException {
                target.write(b);
            }

            public void write(byte b[]) throws IOException {
                target.write(b);
            }

            public void write(byte b[], int off, int len) throws IOException {
                target.write(b, off, len);
            }

            public void flush() throws IOException {
                target.flush();
            }

            public void close() throws IOException {
                target.close();
            }
        });
        this.target = target;
    }

    public void commit() throws IOException {
        target.commit();
    }
}

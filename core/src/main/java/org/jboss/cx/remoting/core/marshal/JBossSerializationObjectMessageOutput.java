package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.io.OutputStream;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.util.ByteMessageOutput;
import org.jboss.cx.remoting.util.ObjectMessageOutput;
import org.jboss.serial.io.JBossObjectOutputStream;

/**
 *
 */
public class JBossSerializationObjectMessageOutput extends JBossObjectOutputStream implements ObjectMessageOutput {

    private final ObjectResolver resolver;
    private final ByteMessageOutput dataMessageOutput;

    public JBossSerializationObjectMessageOutput(final ObjectResolver resolver, final ByteMessageOutput dataMessageOutput) throws IOException {
        super(new OutputStream() {
            public void write(final int b) throws IOException {
                dataMessageOutput.write(b);
            }

            public void write(final byte b[]) throws IOException {
                dataMessageOutput.write(b);
            }

            public void write(final byte b[], final int off, final int len) throws IOException {
                dataMessageOutput.write(b, off, len);
            }

            public void flush() throws IOException {
                dataMessageOutput.flush();
            }

            public void close() throws IOException {
                dataMessageOutput.close();
            }
        });
        enableReplaceObject(true);
        this.resolver = resolver;
        this.dataMessageOutput = dataMessageOutput;
    }

    public void commit() throws IOException {
        flush();
        dataMessageOutput.commit();
    }

    public int getBytesWritten() throws IOException {
        flush();
        return dataMessageOutput.getBytesWritten();
    }

    protected Object replaceObject(final Object obj) throws IOException {
        return resolver.writeReplace(obj);
    }
}

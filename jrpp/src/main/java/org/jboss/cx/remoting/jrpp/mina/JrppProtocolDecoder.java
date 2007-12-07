package org.jboss.cx.remoting.jrpp.mina;

import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.AttributeKey;
import org.jboss.serial.io.JBossObjectInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppProtocolDecoder implements ProtocolDecoder, ProtocolEncoder {

    private static final AttributeKey OBJECT_INPUT_STREAM_KEY = new AttributeKey(JrppProtocolDecoder.class, "objectInputStreamKey");

    private JrppObjectInputStream getObjectInputStream(IoSession session) {
        return (JrppObjectInputStream) session.getAttribute(OBJECT_INPUT_STREAM_KEY);
    }

    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        final JrppObjectInputStream ois = getObjectInputStream(session);
        ois.setInputStream(in.asInputStream());
        ois.reset();
    }

    public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
        // nothing
    }

    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
    }

    public void dispose(IoSession session) throws Exception {
        getObjectInputStream(session).close();
    }

    private static final class JrppObjectInputStream extends JBossObjectInputStream {
        private DelegatingInputStream inputStream;

        private JrppObjectInputStream(DelegatingInputStream inputStream, ClassLoader loader) throws IOException {
            super(inputStream, loader);
            this.inputStream = inputStream;
        }

        public JrppObjectInputStream(final ClassLoader loader) throws IOException {
            this(new DelegatingInputStream(), loader);
        }

        protected Class<?> resolveProxyClass(String[] interfaces) throws IOException, ClassNotFoundException {
            return super.resolveProxyClass(interfaces);
        }

        public Object readObjectOverride() throws IOException, ClassNotFoundException {
            final Object o = super.readObjectOverride();
            if (o instanceof StreamMarker) {
                //uh ,do something
                return null;
            } else {
                return o;
            }
        }

        private void setInputStream(InputStream newInputStream) {
            inputStream.target = newInputStream;
        }
    }

    private static final class DelegatingInputStream extends InputStream {
        private final JrppObjectInputStream objectInputStream;
        private InputStream target;

        public DelegatingInputStream() throws IOException {
            objectInputStream = new JrppObjectInputStream(this, null);
        }

        public int read() throws IOException {
            return target.read();
        }

        public int read(final byte[] b) throws IOException {
            return target.read(b);
        }

        public int read(final byte[] b, final int off, final int len) throws IOException {
            return target.read(b, off, len);
        }

        public long skip(final long n) throws IOException {
            return target.skip(n);
        }

        public int available() throws IOException {
            return target.available();
        }

        public void close() throws IOException {
            target.close();
        }

        public void mark(final int readlimit) {
            target.mark(readlimit);
        }

        public void reset() throws IOException {
            target.reset();
        }

        public boolean markSupported() {
            return target.markSupported();
        }

    }
}


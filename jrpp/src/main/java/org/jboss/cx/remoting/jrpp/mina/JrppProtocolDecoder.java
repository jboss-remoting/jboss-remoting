package org.jboss.cx.remoting.jrpp.mina;

import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.filter.codec.ProtocolDecoderOutput;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.common.AttributeKey;
import org.jboss.serial.io.JBossObjectInputStream;
import org.jboss.cx.remoting.jrpp.msg.JrppRequest;
import org.jboss.cx.remoting.jrpp.msg.JrppReply;
import org.jboss.cx.remoting.jrpp.msg.JrppCancelRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCancelAcknowledgeMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseContextMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseServiceMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseStreamMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppExceptionMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppServiceActivateMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppServiceRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppStreamDataMessage;
import java.io.InputStream;
import java.io.IOException;

/**
 *
 */
public final class JrppProtocolDecoder implements ProtocolDecoder {

    private static final AttributeKey OBJECT_INPUT_STREAM_KEY = new AttributeKey(JrppProtocolDecoder.class, "objectInputStreamKey");

    private JrppObjectInputStream getObjectInputStream(IoSession session) {
        return (JrppObjectInputStream) session.getAttribute(OBJECT_INPUT_STREAM_KEY);
    }

    public void decode(IoSession session, IoBuffer in, ProtocolDecoderOutput out) throws Exception {
        int v = in.get();
        final JrppObjectInputStream ois = getObjectInputStream(session);
        switch (v) {
            case 0:
                out.write(new JrppRequest(ois));
                return;
            case 1:
                out.write(new JrppReply(ois));
                return;
            case 2:
                out.write(new JrppCancelRequestMessage(ois));
                return;
            case 3:
                out.write(new JrppCancelAcknowledgeMessage(ois));
                return;
            case 4:
                out.write(new JrppCloseContextMessage(ois));
                return;
            case 5:
                out.write(new JrppCloseRequestMessage(ois));
                return;
            case 6:
                out.write(new JrppCloseServiceMessage(ois));
                return;
            case 7:
                out.write(new JrppCloseStreamMessage(ois));
                return;
            case 8:
                out.write(new JrppExceptionMessage(ois));
                return;
            case 9:
                out.write(new JrppServiceActivateMessage(ois));
                return;
            case 10:
                out.write(new JrppServiceRequestMessage(ois));
                return;
            case 11:
                out.write(new JrppStreamDataMessage(ois));
                return;
            default:
                throw new IOException("Corrupted stream (wrong message type)");
        }
    }

    public void finishDecode(IoSession session, ProtocolDecoderOutput out) throws Exception {
        // nothing
    }

    public void dispose(IoSession session) throws Exception {
        getObjectInputStream(session).close();
    }

    private static final class JrppObjectInputStream extends JBossObjectInputStream {
        public JrppObjectInputStream(final InputStream is, final ClassLoader loader) throws IOException {
            super(is, loader);
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
    }
}


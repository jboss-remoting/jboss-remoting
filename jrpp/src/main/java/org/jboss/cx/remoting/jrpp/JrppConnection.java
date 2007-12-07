package org.jboss.cx.remoting.jrpp;

import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;
import org.apache.mina.common.IoBuffer;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Header;
import org.jboss.cx.remoting.BasicMessage;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.jrpp.id.IdentifierManager;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppServiceIdentifier;
import org.jboss.cx.remoting.jrpp.mina.StreamMarker;
import org.jboss.serial.io.JBossObjectInputStream;
import org.jboss.serial.io.JBossObjectOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectInputStream;
import java.util.Collection;
import java.util.Set;

/**
 *
 */
public final class JrppConnection {
    private final IoSession ioSession;
    private final ProtocolHandler protocolHandler;
    private final ProtocolContext protocolContext;
    private final SingleSessionIoHandler ioHandler;
    private final IdentifierManager identifierManager;
    private final JrppObjectOutputStream objectOutputStream;

    public JrppConnection(final IoSession ioSession, final ProtocolContext protocolContext) throws IOException {
        this.ioSession = ioSession;
        this.protocolContext = protocolContext;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();
        identifierManager = new IdentifierManager();
        objectOutputStream = new JrppObjectOutputStream(new BufferedOutputStream(new IoSessionOutputStream()), false);
    }

    public JrppConnection(final IoSession ioSession, final ProtocolServerContext serverContext) throws IOException {
        this.ioSession = ioSession;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();

        protocolContext = serverContext.establishSession(protocolHandler);
        identifierManager = new IdentifierManager();
        objectOutputStream = new JrppObjectOutputStream(new IoSessionOutputStream(), false);
    }

    public IoSession getIoSession() {
        return ioSession;
    }

    public SingleSessionIoHandler getIoHandler() {
        return ioHandler;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    private static final Header[] emptyHeaders = new Header[0];

    private final class JrppObjectInputStream extends JBossObjectInputStream {
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

    private final class JrppObjectOutputStream extends JBossObjectOutputStream {
        public JrppObjectOutputStream(final OutputStream output, final boolean checkSerializableClass) throws IOException {
            super(output, checkSerializableClass);
        }

        protected Object replaceObject(Object obj) throws IOException {
            return super.replaceObject(obj);
        }

        public void flush() throws IOException {
            super.flush();
            reset();
        }
    }

    private final class IoSessionOutputStream extends OutputStream {
        private IoBuffer target;

        public IoSessionOutputStream() {
            allocate();
        }

        public void write(int b) throws IOException {
            target.put((byte)b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            target.put(b, off, len);
        }

        public void flush() throws IOException {
            if (target.position() > 0) {
                ioSession.write(target.flip());
                allocate();
            }
        }

        private void allocate() {
            target = IoBuffer.allocate(2048);
        }
    }

    public final class RemotingProtocolHandler implements ProtocolHandler {

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            return new JrppContextIdentifier(identifierManager.getIdentifier());
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            return new JrppRequestIdentifier(identifierManager.getIdentifier());
        }

        public StreamIdentifier openStream() throws IOException {
            return new JrppStreamIdentifier(identifierManager.getIdentifier());
        }

        public ServiceIdentifier openService() throws IOException {
            return new JrppServiceIdentifier(identifierManager.getIdentifier());
        }

        public void closeSession() throws IOException {
            // todo - maybe we don't need to wait?
            ioSession.close().awaitUninterruptibly();
        }

        private void write(MessageType messageType) throws IOException {
            objectOutputStream.writeByte(messageType.ordinal());
        }

        private void write(ServiceIdentifier serviceIdentifier) throws IOException {
            objectOutputStream.writeShort(((JrppServiceIdentifier)serviceIdentifier).getId());
        }

        private void write(ContextIdentifier contextIdentifier) throws IOException {
            objectOutputStream.writeShort(((JrppContextIdentifier)contextIdentifier).getId());
        }

        private void write(StreamIdentifier streamIdentifier) throws IOException {
            objectOutputStream.writeShort(((JrppStreamIdentifier)streamIdentifier).getId());
        }

        private void write(RequestIdentifier requestIdentifier) throws IOException {
            objectOutputStream.writeShort(((JrppRequestIdentifier)requestIdentifier).getId());
        }

        private void write(BasicMessage<?> message) throws IOException {
            objectOutputStream.writeObject(message.getBody());
            final Collection<Header> headers = message.getHeaders();
            objectOutputStream.writeInt(headers.size());
            for (Header header : headers) {
                objectOutputStream.writeUTF(header.getName());
                objectOutputStream.writeUTF(header.getValue());
            }
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
            write(MessageType.CLOSE_SERVICE);
            write(serviceIdentifier);
            objectOutputStream.flush();
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            write(MessageType.CLOSE_CONTEXT);
            write(contextIdentifier);
            objectOutputStream.flush();
        }

        public void closeStream(StreamIdentifier streamIdentifier) throws IOException {
            if (true /* todo if close not already sent */) {
                // todo mark as sent or remove from table
                write(MessageType.CLOSE_STREAM);
                write(streamIdentifier);
                objectOutputStream.flush();
            }
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            write(MessageType.SERVICE_REQUEST);
            write(serviceIdentifier);
            objectOutputStream.writeObject(locator.getRequestType());
            objectOutputStream.writeObject(locator.getReplyType());
            objectOutputStream.writeObject(locator.getEndpointName());
            objectOutputStream.writeObject(locator.getServiceType());
            objectOutputStream.writeObject(locator.getServiceGroupName());
            final Set<String> interceptors = locator.getAvailableInterceptors();
            final int cnt = interceptors.size();
            objectOutputStream.writeInt(cnt);
            for (String name : interceptors) {
                objectOutputStream.writeUTF(name);
            }
            objectOutputStream.flush();
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            write(MessageType.SERVICE_ACTIVATE);
            write(serviceIdentifier);
            objectOutputStream.flush();
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            write(MessageType.REPLY);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            write(reply);
            objectOutputStream.flush();
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            write(MessageType.EXCEPTION);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            objectOutputStream.writeObject(exception);
            objectOutputStream.flush();
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException {
            write(MessageType.REQUEST);
            write(contextIdentifier);
            write(requestIdentifier);
            write(request);
            objectOutputStream.flush();
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            write(MessageType.CANCEL_ACK);
            write(remoteContextIdentifier);
            write(requestIdentifier);
            objectOutputStream.flush();
        }

        public void sendServiceTerminate(ServiceIdentifier remoteServiceIdentifier) throws IOException {
            write(MessageType.SERVICE_TERMINATE);
            write(remoteServiceIdentifier);
            objectOutputStream.flush();
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            write(MessageType.CANCEL_REQ);
            write(contextIdentifier);
            write(requestIdentifier);
            objectOutputStream.writeBoolean(mayInterrupt);
            objectOutputStream.flush();
        }

        public void sendStreamData(StreamIdentifier streamIdentifier, Object data) throws IOException {
            write(MessageType.STREAM_DATA);
            write(streamIdentifier);
            objectOutputStream.writeObject(data);
            objectOutputStream.flush();
        }
    }

    private final class IoHandlerImpl implements SingleSessionIoHandler {
        public void sessionCreated() {
        }

        public void sessionOpened() {
        }

        public void sessionClosed() {
            protocolContext.closeSession();
        }

        public void sessionIdle(IdleStatus idleStatus) {
        }

        public void exceptionCaught(Throwable throwable) {
            // todo log exception
        }

        private void readHeaders(ObjectInputStream ois, BasicMessage<?> msg) throws IOException {
            final int cnt = ois.readInt();
            for (int i = 0; i < cnt; i ++) {
                final String name = ois.readUTF();
                final String value = ois.readUTF();
                msg.addHeader(name, value);
            }
        }

        private ContextIdentifier readCtxtId(ObjectInputStream ois) throws IOException {
            return new JrppContextIdentifier(ois.readShort());
        }

        private ServiceIdentifier readSvcId(ObjectInputStream ois) throws IOException {
            return new JrppServiceIdentifier(ois.readShort());
        }

        private StreamIdentifier readStrId(ObjectInputStream ois) throws IOException {
            return new JrppStreamIdentifier(ois.readShort());
        }

        private RequestIdentifier readReqId(ObjectInputStream ois) throws IOException {
            return new JrppRequestIdentifier(ois.readShort());
        }

        public void messageReceived(Object message) throws Exception {
            IoBuffer buf = (IoBuffer) message;
            final JrppObjectInputStream ois = new JrppObjectInputStream(buf.asInputStream(), null /* todo */);
            final MessageType type = MessageType.values()[ois.readByte() & 0xff];
            switch (type) {
                case CANCEL_ACK: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    final RequestIdentifier requestIdentifier = readReqId(ois);
                    protocolContext.receiveCancelAcknowledge(contextIdentifier, requestIdentifier);
                    break;
                }
                case CANCEL_REQ: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    final RequestIdentifier requestIdentifier = readReqId(ois);
                    final boolean mayInterrupt = ois.readBoolean();
                    protocolContext.receiveCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
                    break;
                }
                case CLOSE_CONTEXT: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    protocolContext.closeContext(contextIdentifier);
                    break;
                }
                case CLOSE_SERVICE: {
                    final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                    protocolContext.closeService(serviceIdentifier);
                    break;
                }
                case CLOSE_STREAM: {
                    final StreamIdentifier streamIdentifier = readStrId(ois);
                    protocolContext.closeStream(streamIdentifier);
                    break;
                }
                case EXCEPTION: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    final RequestIdentifier requestIdentifier = readReqId(ois);
                    final RemoteExecutionException exception = (RemoteExecutionException) ois.readObject();
                    protocolContext.receiveException(contextIdentifier, requestIdentifier, exception);
                    break;
                }
                case REPLY: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    final RequestIdentifier requestIdentifier = readReqId(ois);
                    final Reply<?> reply = protocolContext.createReply(ois.readObject());
                    readHeaders(ois, reply);
                    protocolContext.receiveReply(contextIdentifier, requestIdentifier, reply);
                    break;
                }
                case REQUEST: {
                    final ContextIdentifier contextIdentifier = readCtxtId(ois);
                    final RequestIdentifier requestIdentifier = readReqId(ois);
                    final Request<?> request = protocolContext.createRequest(ois.readObject());
                    readHeaders(ois, request);
                    protocolContext.receiveRequest(contextIdentifier, requestIdentifier, request);
                    break;
                }
                case SERVICE_ACTIVATE: {
                    final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                    protocolContext.receiveServiceActivate(serviceIdentifier);
                    break;
                }
                case SERVICE_REQUEST: {
                    final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                    final Class<?> requestType = (Class<?>) ois.readObject();
                    final Class<?> replyType = (Class<?>) ois.readObject();
                    final String endpointName = ois.readUTF();
                    final String serviceType = ois.readUTF();
                    final String serviceGroupName = ois.readUTF();
                    final Set<String> interceptors = CollectionUtil.hashSet();
                    int c = ois.readInt();
                    for (int i = 0; i < c; i ++) {
                        interceptors.add(ois.readUTF());
                    }
                    final ServiceLocator<?, ?> locator = ServiceLocator.DEFAULT
                            .setRequestType(requestType)
                            .setReplyType(replyType)
                            .setEndpointName(endpointName)
                            .setServiceType(serviceType)
                            .setServiceGroupName(serviceGroupName)
                            .setAvailableInterceptors(interceptors);
                    protocolContext.receiveServiceRequest(serviceIdentifier, locator);
                    break;
                }
                case SERVICE_TERMINATE: {
                    final ServiceIdentifier serviceIdentifier = readSvcId(ois);
                    protocolContext.receiveServiceTerminate(serviceIdentifier);
                    break;
                }
                case STREAM_DATA: {
                    final StreamIdentifier streamIdentifier = readStrId(ois);
                    final Object data = ois.readObject();
                    protocolContext.receiveStreamData(streamIdentifier, data);
                    break;
                }
            }
        }

        public void messageSent(Object object) {
        }
    }

    /**
     * Keep elements in order.  If an element is to be deleted, replace it with a placeholder.
     */
    private enum MessageType {
        CANCEL_ACK,
        CANCEL_REQ,
        CLOSE_CONTEXT,
        CLOSE_SERVICE,
        CLOSE_STREAM,
        EXCEPTION,
        REPLY,
        REQUEST,
        SERVICE_ACTIVATE,
        SERVICE_REQUEST,
        SERVICE_TERMINATE,
        STREAM_DATA,
    }

}

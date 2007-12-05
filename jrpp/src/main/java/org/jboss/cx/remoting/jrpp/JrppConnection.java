package org.jboss.cx.remoting.jrpp;

import org.apache.mina.common.IoSession;
import org.apache.mina.common.IdleStatus;
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
import org.jboss.cx.remoting.jrpp.msg.JrppCloseServiceMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseContextMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppMessageVisitorIoHandler;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppServiceActivateMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppServiceRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCloseStreamMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppRequest;
import org.jboss.cx.remoting.jrpp.msg.JrppReply;
import org.jboss.cx.remoting.jrpp.msg.JrppExceptionMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCancelAcknowledgeMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppCancelRequestMessage;
import org.jboss.cx.remoting.jrpp.msg.JrppStreamDataMessage;
import org.jboss.cx.remoting.jrpp.id.IdentifierManager;
import org.jboss.cx.remoting.jrpp.id.JrppContextIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppRequestIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppStreamIdentifier;
import org.jboss.cx.remoting.jrpp.id.JrppServiceIdentifier;
import java.io.IOException;

/**
 *
 */
public final class JrppConnection {
    private final IoSession ioSession;
    private final ProtocolHandler protocolHandler;
    private final ProtocolContext protocolContext;
    private final SingleSessionIoHandler ioHandler;
    private final IdentifierManager identifierManager;

    public JrppConnection(final IoSession ioSession, final ProtocolContext protocolContext) {
        this.ioSession = ioSession;
        this.protocolContext = protocolContext;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();
        identifierManager = new IdentifierManager();
    }

    public JrppConnection(final IoSession ioSession, final ProtocolServerContext serverContext) {
        this.ioSession = ioSession;

        protocolHandler = new RemotingProtocolHandler();
        ioHandler = new IoHandlerImpl();

        protocolContext = serverContext.establishSession(protocolHandler);
        identifierManager = new IdentifierManager();
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

    public final class RemotingProtocolHandler implements ProtocolHandler {

        public ContextIdentifier openContext(ServiceIdentifier serviceIdentifier) throws IOException {
            return new JrppContextIdentifier(identifierManager);
        }

        public RequestIdentifier openRequest(ContextIdentifier contextIdentifier) throws IOException {
            return new JrppRequestIdentifier(identifierManager);
        }

        public StreamIdentifier openStream(ContextIdentifier contextIdentifier) throws IOException {
            return new JrppStreamIdentifier(identifierManager);
        }

        public ServiceIdentifier openService() throws IOException {
            return new JrppServiceIdentifier(identifierManager);
        }

        public void closeSession() throws IOException {
            // todo - maybe we don't need to wait?
            ioSession.close().awaitUninterruptibly();
        }

        public void closeService(ServiceIdentifier serviceIdentifier) throws IOException {
            ioSession.write(new JrppCloseServiceMessage(serviceIdentifier));
            ((JrppServiceIdentifier)serviceIdentifier).close();
        }

        public void closeContext(ContextIdentifier contextIdentifier) throws IOException {
            ioSession.write(new JrppCloseContextMessage(contextIdentifier));
            ((JrppContextIdentifier)contextIdentifier).close();
        }

        public void closeRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            ioSession.write(new JrppCloseRequestMessage(contextIdentifier, requestIdentifier));
            ((JrppRequestIdentifier)requestIdentifier).close();
        }

        public void closeStream(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier) throws IOException {
            ioSession.write(new JrppCloseStreamMessage(contextIdentifier, streamIdentifier));
            ((JrppStreamIdentifier)streamIdentifier).close();
        }

        public void sendServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) throws IOException {
            ioSession.write(new JrppServiceRequestMessage(serviceIdentifier, locator));
        }

        public void sendServiceActivate(ServiceIdentifier serviceIdentifier) throws IOException {
            ioSession.write(new JrppServiceActivateMessage(serviceIdentifier));
        }

        public void sendReply(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) throws IOException {
            final Header[] headers = reply.getHeaders().toArray(emptyHeaders);
            ioSession.write(new JrppReply(remoteContextIdentifier, requestIdentifier, reply.getBody(), headers));
        }

        public void sendException(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) throws IOException {
            ioSession.write(new JrppExceptionMessage(remoteContextIdentifier, requestIdentifier, exception));
        }

        public void sendRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) throws IOException {
            final Header[] headers = request.getHeaders().toArray(emptyHeaders);
            ioSession.write(new JrppRequest(contextIdentifier, requestIdentifier, request.getBody(), headers));
        }

        public void sendCancelAcknowledge(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier) throws IOException {
            ioSession.write(new JrppCancelAcknowledgeMessage(remoteContextIdentifier, requestIdentifier));
        }

        public void sendCancelRequest(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) throws IOException {
            ioSession.write(new JrppCancelRequestMessage(contextIdentifier, requestIdentifier, mayInterrupt));
        }

        public void sendStreamData(ContextIdentifier contextIdentifier, StreamIdentifier streamIdentifier, Object data) throws IOException {
            ioSession.write(new JrppStreamDataMessage(contextIdentifier, streamIdentifier, data));
        }
    }

    private final class IoHandlerImpl extends JrppMessageVisitorIoHandler {
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
            protocolContext.failSession();
        }

        public void messageSent(Object object) {
        }

        public void visit(JrppCloseServiceMessage msg) {
            protocolContext.closeService(msg.getServiceIdentifier());
        }

        public void visit(JrppCloseContextMessage msg) {
            protocolContext.closeContext(msg.getContextIdentifier());
        }

        public void visit(JrppCloseRequestMessage msg) {
            // todo
        }

        public void visit(JrppServiceActivateMessage msg) {
            protocolContext.receiveServiceActivate(msg.getServiceIdentifier());
        }

        public void visit(JrppServiceRequestMessage msg) {
            protocolContext.receiveServiceRequest(msg.getServiceIdentifier(), msg.getServiceLocator());
        }

        public void visit(JrppCloseStreamMessage msg) {
            protocolContext.closeStream(msg.getContextIdentifier(), msg.getStreamIdentifier());
        }

        public void visit(JrppRequest msg) {
            protocolContext.receiveRequest(msg.getContextIdentifier(), msg.getRequestIdentifier(), protocolContext.createRequest(msg.getBody()));
        }

        public void visit(JrppReply msg) {
            protocolContext.receiveReply(msg.getContextIdentifier(), msg.getRequestIdentifier(), protocolContext.createReply(msg.getBody()));
        }

        public void visit(JrppExceptionMessage msg) {
            protocolContext.receiveException(msg.getContextIdentifier(), msg.getRequestIdentifier(), msg.getException());
        }

        public void visit(JrppCancelAcknowledgeMessage msg) {
            protocolContext.receiveCancelAcknowledge(msg.getContextIdentifier(), msg.getRequestIdentifier());
        }

        public void visit(JrppCancelRequestMessage msg) {
            protocolContext.receiveCancelRequest(msg.getContextIdentifier(), msg.getRequestIdentifier(), msg.isMayInterrupt());
        }

        public void visit(JrppStreamDataMessage msg) {
            protocolContext.receiveStreamData(msg.getContextIdentifier(), msg.getStreamIdentifier(), msg.getData());
        }
    }
}

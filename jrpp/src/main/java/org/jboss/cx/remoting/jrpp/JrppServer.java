package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoProcessor;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filter.sasl.SaslMessageSender;
import org.apache.mina.filter.sasl.SaslServerFilter;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.jrpp.mina.FramingIoFilter;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;

/**
 *
 */
public final class JrppServer {
    private final IoHandler ioHandler = new SingleSessionIoHandlerDelegate(new ServerSessionHandlerFactory());

    // injected properties

    /** The server socket address.  Set before {@code create}. */
    private SocketAddress socketAddress;
    /** Protocol support object.  Set before {@code create}. */
    private JrppProtocolSupport protocolSupport;

    // calculated properties

    /** Executor.  Set upon {@code create}. */
    private Executor executor;
    /** NIO Processor.  Set upon {@code create}. */
    private IoProcessor ioProcessor;
    /** IO Acceptor.  Set upon {@code create}. */
    private IoAcceptor ioAcceptor;
    /** Attribute map.  Set before {@code create}. */
    private AttributeMap attributeMap;
    /** Endpoint.  Set before {@code create}. */
    private Endpoint endpoint;

    // Accessors

    public SocketAddress getSocketAddress() {
        return socketAddress;
    }

    public void setSocketAddress(final SocketAddress socketAddress) {
        this.socketAddress = socketAddress;
    }

    public JrppProtocolSupport getProtocolSupport() {
        return protocolSupport;
    }

    public void setProtocolSupport(final JrppProtocolSupport protocolSupport) {
        this.protocolSupport = protocolSupport;
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }

    public void setAttributeMap(final AttributeMap attributeMap) {
        this.attributeMap = attributeMap;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    // Lifecycle

    @SuppressWarnings ({"unchecked"})
    public void create() {
        executor = protocolSupport.getExecutor();
        ioProcessor = protocolSupport.getIoProcessor();
        ioAcceptor = new NioSocketAcceptor(executor, ioProcessor);
        ioAcceptor.setDefaultLocalAddress(socketAddress);
        ioAcceptor.setHandler(ioHandler);
        ioAcceptor.getFilterChain().addLast("framing filter", new FramingIoFilter());
        ioAcceptor.getFilterChain().addLast(JrppConnection.SASL_SERVER_FILTER_NAME, new SaslServerFilter(new SaslMessageSender() {
            public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                JrppConnection.getConnection(ioSession).sendChallenge(rawMsgData);
            }
        }));
        ioAcceptor.getFilterChain().addLast("debug 0", new LoggingFilter());
    }

    public void start() throws IOException {
        ioAcceptor.bind();
    }

    public void stop() {
        ioAcceptor.unbind();
    }

    public void destroy() {
        ioAcceptor.dispose();
        ioAcceptor = null;
        ioProcessor = null;
        executor = null;
    }

    // MINA support

    private final class ServerSessionHandlerFactory implements SingleSessionIoHandlerFactory {
        public SingleSessionIoHandler getHandler(IoSession ioSession) throws IOException {
            final JrppConnection connection = new JrppConnection(attributeMap);
            connection.initializeServer(ioSession);
            final ProtocolContext protocolContext = endpoint.openIncomingSession(connection.getProtocolHandler());
            connection.start(protocolContext);
            return connection.getIoHandler();
        }
    }

}

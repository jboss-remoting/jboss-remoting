package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoSessionInitializer;
import org.apache.mina.common.IoProcessor;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.filter.sasl.SaslClientFilter;
import org.apache.mina.filter.sasl.SaslMessageSender;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.core.util.AttributeMap;
import org.jboss.cx.remoting.jrpp.mina.FramingIoFilter;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;

/**
 *
 */
public final class JrppProtocolSupport {
    private ProtocolHandlerFactoryImpl protocolHandlerFactory;

    /** Thread pool to use.  Set before {@code create}. */
    private Executor executor;
    /** Endpoint.  Set before {@code create}. */
    private Endpoint endpoint;

    /** The NIO processor.  Set upon {@code create}. */
    private IoProcessor ioProcessor;
    /** Protocol server context.  Set upon {@code create}. */
    private ProtocolServerContext serverContext;
    /** Protocol registration.  Set upon {@code create}. */
    private ProtocolRegistration registration;

    public JrppProtocolSupport() {
    }

    // Accessors

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(final Executor executor) {
        this.executor = executor;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    // Package getters

    IoProcessor getIoProcessor() {
        return ioProcessor;
    }

    ProtocolServerContext getServerContext() {
        return serverContext;
    }

    // Lifecycle

    public void create() throws RemotingException {
        ioProcessor = new NioProcessor(executor);
        protocolHandlerFactory = new ProtocolHandlerFactoryImpl();
        final ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("jrpp").setProtocolHandlerFactory(protocolHandlerFactory);
        final ProtocolRegistration registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
        this.registration = registration;
    }

    public void start() {
        registration.start();
    }

    public void stop() {
        registration.stop();
    }

    public void destroy() {
        if (ioProcessor != null) {
            ioProcessor.dispose();
        }
        if (registration != null) {
            registration.unregister();
            registration = null;
        }
        protocolHandlerFactory = null;
        serverContext = null;
    }

    // Utilities

    private SocketAddress getSocketAddressFromUri(final URI uri) {
        // todo - validate host and/or port!
        return new InetSocketAddress(uri.getHost(), uri.getPort());
    }

    /**
     * Protocol handler factory implementation.  There will ever only be one of these.
     */
    private final class ProtocolHandlerFactoryImpl implements ProtocolHandlerFactory, SingleSessionIoHandlerFactory {
        private final IoConnector connector;

        @SuppressWarnings ({"unchecked"})
        public ProtocolHandlerFactoryImpl() {
            connector = new NioSocketConnector(executor, ioProcessor);
            connector.getFilterChain().addLast("framing filter", new FramingIoFilter());
            connector.getFilterChain().addLast(JrppConnection.SASL_CLIENT_FILTER_NAME, new SaslClientFilter(new SaslMessageSender() {
                public void sendSaslMessage(IoSession ioSession, byte[] rawMsgData) throws IOException {
                    JrppConnection.getConnection(ioSession).sendResponse(rawMsgData);
                }
            }));
            connector.getFilterChain().addLast("debug 0", new LoggingFilter());
            connector.setHandler(new SingleSessionIoHandlerDelegate(this));
        }

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(final ProtocolContext context, final URI remoteUri, final AttributeMap attributeMap) throws IOException {
            // todo - add a connect timeout
            // todo - local connect addr
            final JrppConnection jrppConnection = new JrppConnection(attributeMap);
            final SocketAddress serverAddress = getSocketAddressFromUri(remoteUri);
            final ConnectFuture future = connector.connect(serverAddress, new IoSessionInitializer<ConnectFuture>() {
                public void initializeSession(final IoSession ioSession, final ConnectFuture connectFuture) {
                    jrppConnection.initializeClient(ioSession, context);
                }
            });
            future.awaitUninterruptibly();
            return jrppConnection.getProtocolHandler();
        }

        public void close() {
        }

        public SingleSessionIoHandler getHandler(IoSession session) throws Exception {
            return JrppConnection.getHandler(session);
        }
    }
}

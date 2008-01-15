package org.jboss.cx.remoting.jrpp;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoSession;
import org.apache.mina.filter.logging.LoggingFilter;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.transport.socket.nio.NioProcessor;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointShutdownListener;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.jrpp.mina.FramingIoFilter;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class JrppProtocolSupport {
    @SuppressWarnings ({"UnusedDeclaration"})
    private final Endpoint endpoint;
    private final ProtocolServerContext serverContext;
    private final IoHandler serverIoHandler = new SingleSessionIoHandlerDelegate(new ServerSessionHandlerFactory());
    private final Set<IoAcceptor> ioAcceptors = CollectionUtil.synchronizedSet(CollectionUtil.<IoAcceptor>hashSet());
    // todo - make the thread pools configurable
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final NioProcessor nioProcessor = new NioProcessor(threadPool);
    private final ProtocolHandlerFactoryImpl protocolHandlerFactory = new ProtocolHandlerFactoryImpl();

    public JrppProtocolSupport(final Endpoint endpoint) throws RemotingException {
        final ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("jrpp").setProtocolHandlerFactory(protocolHandlerFactory);
        final ProtocolRegistration registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
        this.endpoint = endpoint;
        endpoint.addShutdownListener(new EndpointShutdownListener() {
            public void handleShutdown(Endpoint endpoint) {
                shutdown();
            }
        });
    }

    public void addServer(final SocketAddress address) throws IOException {
        // todo - make the acceptor managable so it can be started and stopped
        final IoAcceptor ioAcceptor = new NioSocketAcceptor(threadPool, nioProcessor);
        ioAcceptor.setDefaultLocalAddress(address);
        ioAcceptor.setHandler(serverIoHandler);
        ioAcceptor.getFilterChain().addLast("framing filter", new FramingIoFilter());
        ioAcceptor.getFilterChain().addLast("debug 0", new LoggingFilter());
        ioAcceptor.bind();
        ioAcceptors.add(ioAcceptor);
    }

    public void shutdown() {
        for (IoAcceptor acceptor : ioAcceptors) {
            acceptor.unbind();
        }
        for (IoAcceptor acceptor : ioAcceptors) {
            acceptor.dispose();
        }
        ioAcceptors.clear();
        protocolHandlerFactory.connector.dispose();
        threadPool.shutdown();
    }

    private final class ServerSessionHandlerFactory implements SingleSessionIoHandlerFactory {
        public SingleSessionIoHandler getHandler(IoSession ioSession) throws IOException {
            final JrppConnection connection;
            connection = new JrppConnection(ioSession, serverContext, endpoint.getRemoteCallbackHandler());
            return connection.getIoHandler();
        }
    }

    /**
     * Protocol handler factory implementation.  There will ever only be one of these.
     */
    private final class ProtocolHandlerFactoryImpl implements ProtocolHandlerFactory, SingleSessionIoHandlerFactory {
        private final IoConnector connector;

        public ProtocolHandlerFactoryImpl() {
            connector = new NioSocketConnector(threadPool, nioProcessor);
            connector.getFilterChain().addLast("framing filter", new FramingIoFilter());
            connector.getFilterChain().addLast("debug 0", new LoggingFilter());
            connector.setHandler(new SingleSessionIoHandlerDelegate(this));
        }

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, CallbackHandler clientCallbackHandler) throws IOException {
            // todo - add a connect timeout
            // todo - local connect addr
            final JrppConnection jrppConnection = new JrppConnection(connector, remoteUri, context, clientCallbackHandler);
            if (jrppConnection.waitForUp()) {
                return jrppConnection.getProtocolHandler();
            } else {
                throw new IOException("Failed to initiate a JRPP connection");
            }
        }

        public void close() {
            shutdown();
        }

        public SingleSessionIoHandler getHandler(IoSession session) throws Exception {
            return JrppConnection.getHandler(session);
        }
    }
}

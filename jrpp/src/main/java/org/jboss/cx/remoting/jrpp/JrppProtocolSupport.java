package org.jboss.cx.remoting.jrpp;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolServerContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.apache.mina.common.IoAcceptor;
import org.apache.mina.common.IoSession;
import org.apache.mina.common.IoConnector;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoHandler;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.util.Set;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public final class JrppProtocolSupport {
    private final ProtocolServerContext serverContext;
    private final IoHandler ioHandler = new SingleSessionIoHandlerDelegate(new SessionHandlerFactory());
    private final Set<IoAcceptor> ioAcceptors = CollectionUtil.hashSet();

    public JrppProtocolSupport(final Endpoint endpoint) throws RemotingException {
        final ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("jrpp").setProtocolHandlerFactory(new ProtocolHandlerFactoryImpl());
        final ProtocolRegistration registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
        // todo - add a hook to protocol registration for deregister notification?
    }

    public void addServer(final SocketAddress address) throws IOException {
        // todo - make the acceptor managable so it can be started and stopped
        final IoAcceptor ioAcceptor = new NioSocketAcceptor();
        ioAcceptor.setLocalAddress(address);
        ioAcceptor.setHandler(ioHandler);
        ioAcceptor.bind();
        ioAcceptors.add(ioAcceptor);
    }

    private final class SessionHandlerFactory implements SingleSessionIoHandlerFactory {
        public SingleSessionIoHandler getHandler(IoSession ioSession) {
            final JrppConnection connection = new JrppConnection(ioSession, serverContext);
            return connection.getIoHandler();
        }
    }

    /**
     * Protocol handler factory implementation.  There will ever only be one of these.
     */
    private final class ProtocolHandlerFactoryImpl implements ProtocolHandlerFactory {
        private final IoConnector connector;

        public ProtocolHandlerFactoryImpl(final IoConnector connector) {
            this.connector = connector;
            connector.setHandler(ioHandler);
        }

        public ProtocolHandlerFactoryImpl() {
            connector = new NioSocketConnector();
            connector.setHandler(ioHandler);
        }

        public boolean isLocal(URI uri) {
            return false;
        }

        public ProtocolHandler createHandler(ProtocolContext context, URI remoteUri, CallbackHandler clientCallbackHandler, CallbackHandler serverCallbackHandler) throws IOException {
            // todo - add a connect timeout
            // todo - local connect addr
            final InetSocketAddress socketAddress = new InetSocketAddress(remoteUri.getHost(), remoteUri.getPort());
            final ConnectFuture future = connector.connect(socketAddress).awaitUninterruptibly();
            return new JrppConnection(future.getSession(), context).getProtocolHandler();
        }

        public void close() {
            connector.dispose();
            for (IoAcceptor ioAcceptor : ioAcceptors) {
                ioAcceptor.dispose();
                ioAcceptors.remove(ioAcceptor);
            }
        }
    }
}

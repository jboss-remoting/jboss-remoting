package org.jboss.cx.remoting.jrpp;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.jrpp.mina.FramingIoFilter;
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
import org.apache.mina.common.AttributeKey;
import org.apache.mina.transport.socket.nio.NioSocketAcceptor;
import org.apache.mina.transport.socket.nio.NioSocketConnector;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerDelegate;
import org.apache.mina.handler.multiton.SingleSessionIoHandlerFactory;
import org.apache.mina.handler.multiton.SingleSessionIoHandler;
import org.apache.mina.filter.sasl.SaslServerFilter;
import org.apache.mina.filter.sasl.SaslServerFactory;
import org.apache.mina.filter.sasl.SaslClientFactory;
import org.apache.mina.filter.sasl.SaslClientFilter;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.URI;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.Collections;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;

/**
 *
 */
public final class JrppProtocolSupport {
    private final String endpointName;
    private final ProtocolServerContext serverContext;
    private final IoHandler ioHandler = new SingleSessionIoHandlerDelegate(new SessionHandlerFactory());
    private final Set<IoAcceptor> ioAcceptors = CollectionUtil.hashSet();

    private static final AttributeKey SERVER_CALLBACK_HANDLER = new AttributeKey(JrppProtocolSupport.class, "serverCallbackHandler");
    private static final AttributeKey CLIENT_CALLBACK_HANDLER = new AttributeKey(JrppProtocolSupport.class, "clientCallbackHandler");

    public JrppProtocolSupport(final Endpoint endpoint) throws RemotingException {
        final ProtocolRegistrationSpec spec = ProtocolRegistrationSpec.DEFAULT.setScheme("jrpp").setProtocolHandlerFactory(new ProtocolHandlerFactoryImpl());
        final ProtocolRegistration registration = endpoint.registerProtocol(spec);
        serverContext = registration.getProtocolServerContext();
        // todo - add a hook to protocol registration for deregister notification?
        endpointName = endpoint.getName();
    }

    public void addServer(final SocketAddress address) throws IOException {
        // todo - make the acceptor managable so it can be started and stopped
        final IoAcceptor ioAcceptor = new NioSocketAcceptor();
        ioAcceptor.setDefaultLocalAddress(address);
        ioAcceptor.setHandler(ioHandler);
        ioAcceptor.getFilterChain().addLast("framing filter", new FramingIoFilter());
        ioAcceptor.getFilterChain().addLast("SASL server filter", new SaslServerFilter(new SaslServerMaker(), null));
        ioAcceptor.getFilterChain().addLast("SASL client filter", new SaslClientFilter(new SaslClientMaker(), null));
        ioAcceptor.bind();
        ioAcceptors.add(ioAcceptor);
    }

    private final class SessionHandlerFactory implements SingleSessionIoHandlerFactory {
        public SingleSessionIoHandler getHandler(IoSession ioSession) {
            final JrppConnection connection;
            try {
                connection = new JrppConnection(ioSession, serverContext);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initiate JRPP connection", e);
            }
            return connection.getIoHandler();
        }
    }

    private final class SaslServerMaker implements SaslServerFactory {
        public SaslServer createSaslServer(IoSession ioSession) throws SaslException {
            return Sasl.createSaslServer("SRP", "JRPP", endpointName, Collections.<String, Object>emptyMap(), (CallbackHandler) ioSession.getAttribute(SERVER_CALLBACK_HANDLER));
        }
    }

    private final class SaslClientMaker implements SaslClientFactory {
        public SaslClient createSaslClient(IoSession ioSession) throws SaslException {
            return Sasl.createSaslClient(new String[] { "SRP" }, "authorizId", "JRPP", endpointName, Collections.<String, Object>emptyMap(), (CallbackHandler) ioSession.getAttribute(CLIENT_CALLBACK_HANDLER));
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
            connector.getFilterChain().addLast("framing filter", new FramingIoFilter());
            connector.getFilterChain().addLast("SASL client filter", new SaslClientFilter(new SaslClientMaker(), null));
            connector.getFilterChain().addLast("SASL server filter", new SaslServerFilter(new SaslServerMaker(), null));
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

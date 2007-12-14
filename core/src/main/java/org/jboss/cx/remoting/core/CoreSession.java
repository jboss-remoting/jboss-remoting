package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.Map;
import java.io.IOException;
import java.lang.ref.WeakReference;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.StreamIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandlerFactory;
import org.jboss.cx.remoting.spi.protocol.ProtocolContext;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

import javax.security.auth.callback.CallbackHandler;


/**
 * Three execution contexts:
 *
 * - Inbound protocol handler - controlled by server/network handler
 * - Context client context - controlled by user/container
 * - Local work handler - ExecutorService provided to Endpoint
 */
public final class CoreSession {
    private static final Logger log = Logger.getLogger(CoreSession.class);

    private final ProtocolContextImpl protocolContext = new ProtocolContextImpl();
    private final UserSession userSession = new UserSession();
    private final ConcurrentMap<ContextIdentifier, WeakReference<CoreContext>> contexts = CollectionUtil.concurrentMap();
    private final ConcurrentMap<ContextIdentifier, WeakReference<CoreServerContext>> serverContexts = CollectionUtil.concurrentMap();

    // don't GC the endpoint while a session lives
    private final CoreEndpoint endpoint;
    private final ProtocolHandler protocolHandler;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.UP);

    private enum State {
        DOWN,
        UP,
        STOPPING,
    }

    protected CoreSession(final CoreEndpoint endpoint, final ProtocolHandler protocolHandler) {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        if (protocolHandler == null) {
            throw new NullPointerException("protocolHandler is null");
        }
        this.endpoint = endpoint;
        this.protocolHandler = protocolHandler;
    }

    protected CoreSession(final CoreEndpoint endpoint, final ProtocolHandlerFactory factory, final EndpointLocator endpointLocator) throws IOException {
        if (endpoint == null) {
            throw new NullPointerException("endpoint is null");
        }
        if (factory == null) {
            throw new NullPointerException("factory is null");
        }
        if (endpointLocator == null) {
            throw new NullPointerException("endpointLocator is null");
        }
        this.endpoint = endpoint;
        final CallbackHandler locatorCallbackHandler = endpointLocator.getClientCallbackHandler();
        final Endpoint userEndpoint = endpoint.getUserEndpoint();
        protocolHandler = factory.createHandler(protocolContext, endpointLocator.getEndpointUri(), locatorCallbackHandler == null ? userEndpoint.getLocalCallbackHandler() : locatorCallbackHandler, userEndpoint.getRemoteCallbackHandler());
    }

    public <I, O> CoreContext<I, O> createContext(final ServiceIdentifier serviceIdentifier) throws RemotingException {
        if (serviceIdentifier == null) {
            throw new NullPointerException("serviceIdentifier is null");
        }
        final ContextIdentifier contextIdentifier;
        try {
            contextIdentifier = protocolHandler.openContext(serviceIdentifier);
        } catch (IOException e) {
            RemotingException rex = new RemotingException("Failed to open context: " + e.getMessage());
            rex.setStackTrace(e.getStackTrace());
            throw rex;
        }
        final CoreContext<I, O> context = new CoreContext<I, O>(this, contextIdentifier);
        if (log.isTrace()) {
            log.trace("Adding new context, ID = " + contextIdentifier);
        }
        contexts.put(contextIdentifier, new WeakReference<CoreContext>(context));
        return context;
    }

    protected CoreContext getContext(final ContextIdentifier contextIdentifier) {
        if (contextIdentifier == null) {
            throw new NullPointerException("contextIdentifier is null");
        }
        final WeakReference<CoreContext> weakReference = contexts.get(contextIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    protected CoreServerContext getServerContext(final ContextIdentifier remoteContextIdentifier) {
        if (remoteContextIdentifier == null) {
            throw new NullPointerException("remoteContextIdentifier is null");
        }
        final WeakReference<CoreServerContext> weakReference = serverContexts.get(remoteContextIdentifier);
        return weakReference == null ? null : weakReference.get();
    }

    protected void removeContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        contexts.remove(identifier);
    }

    protected void removeServerContext(final ContextIdentifier identifier) {
        if (identifier == null) {
            throw new NullPointerException("identifier is null");
        }
        serverContexts.remove(identifier);
    }

    public Session getUserSession() {
        return userSession;
    }

    protected void shutdown() {
        if (state.transition(State.UP, State.STOPPING)) {
            for (Map.Entry<ContextIdentifier,WeakReference<CoreContext>> entry : contexts.entrySet()) {
                final CoreContext context = entry.getValue().get();
                if (context != null) {
                    context.shutdown();
                }
            }
            for (Map.Entry<ContextIdentifier,WeakReference<CoreServerContext>> entry : serverContexts.entrySet()) {
                final CoreServerContext context = entry.getValue().get();
                if (context != null) {
                    context.shutdown();
                }
            }
            state.requireTransition(State.STOPPING, State.DOWN);
        }
    }

    public ProtocolContext getProtocolContext() {
        return protocolContext;
    }

    public ProtocolHandler getProtocolHandler() {
        return protocolHandler;
    }

    public final class UserSession implements Session {
        private UserSession() {}

        private final ConcurrentMap<Object, Object> sessionMap = CollectionUtil.concurrentMap();

        public void close() throws RemotingException {
            shutdown();
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return sessionMap;
        }

        public String getEndpointName() {
            return endpoint.getUserEndpoint().getName();
        }

        public <I, O> ContextSource<I, O> openService(ServiceLocator<I, O> locator) {
            if (locator == null) {
                throw new NullPointerException("locator is null");
            }
            final ServiceIdentifier serviceIdentifier;
            try {
                serviceIdentifier = protocolHandler.openService();
            } catch (IOException e) {
                // todo...
                return null;
            }

            // todo
            return new ServiceContextSource<I,O>(serviceIdentifier);
        }
    }

    public final class ServiceContextSource<I, O> implements ContextSource<I, O> {
        private final ServiceIdentifier serviceIdentifier;
        

        private ServiceContextSource(final ServiceIdentifier serviceIdentifier) {
            this.serviceIdentifier = serviceIdentifier;
        }

        public void close() {
        }

        public Context<I, O> createContext() throws RemotingException {
            final ContextIdentifier contextIdentifier;
            try {
                contextIdentifier = protocolHandler.openContext(serviceIdentifier);
            } catch (IOException e) {
                throw new RemotingException("Unable to open context", e);
            }
            CoreContext<I, O> coreContext = CoreSession.this.createContext(serviceIdentifier);
            contexts.put(contextIdentifier, new WeakReference<CoreContext>(coreContext));
            return coreContext.getUserContext();
        }
    }

    public final class ProtocolContextImpl implements ProtocolContext {

        public void closeSession() {
            shutdown();
        }

        public void closeContext(ContextIdentifier remoteContextIdentifier) {
        }

        public void closeStream(StreamIdentifier streamIdentifier) {
        }

        public void closeService(ServiceIdentifier serviceIdentifier) {
        }

        public void receiveServiceRequest(ServiceIdentifier serviceIdentifier, ServiceLocator<?, ?> locator) {
        }

        public void receiveServiceActivate(ServiceIdentifier serviceIdentifier) {
        }

        public void failSession() {
            // todo
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveReply(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, Reply<?> reply) {
            final CoreContext context = getContext(contextIdentifier);
            if (context != null) {
                context.handleInboundReply(requestIdentifier, reply);
            } else {
                if (log.isTrace()) {
                    log.trace("Missing context identifier for inbound exception " + contextIdentifier);
                }
            }
        }

        public void receiveException(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            final CoreContext context = getContext(contextIdentifier);
            if (context != null) {
                context.handleInboundException(requestIdentifier, exception);
            } else {
                if (log.isTrace()) {
                    log.trace("Missing context identifier for inbound exception " + contextIdentifier);
                }
            }
        }

        @SuppressWarnings ({"unchecked"})
        public void receiveRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, Request<?> request) {
            final CoreServerContext context = getServerContext(remoteContextIdentifier);
            if (context != null) {
                context.getLastInterceptor().processInboundRequest(null, requestIdentifier, request);
            } else {
                if (log.isTrace()) {
                    log.trace("Missing context identifier for inbound request " + remoteContextIdentifier);
                }
                try {
                    protocolHandler.sendException(remoteContextIdentifier, requestIdentifier, new RemoteExecutionException("Received a request on an invalid context"));
                } catch (IOException e) {
                    log.trace("Failed to send exception", e);
                }
            }
        }

        public void receiveCancelAcknowledge(ContextIdentifier contextIdentifier, RequestIdentifier requestIdentifier) {
        }

        public void receiveServiceTerminate(ServiceIdentifier serviceIdentifier) {
        }

        public void receiveCancelRequest(ContextIdentifier remoteContextIdentifier, RequestIdentifier requestIdentifier, boolean mayInterrupt) {
        }

        public void receiveStreamData(StreamIdentifier streamIdentifier, final Object data) {
        }

        public <T> Reply<T> createReply(T body) {
            return new ReplyImpl<T>(body);
        }

        public <T> Request<T> createRequest(T body) {
            return new RequestImpl<T>(body);
        }
    }
}

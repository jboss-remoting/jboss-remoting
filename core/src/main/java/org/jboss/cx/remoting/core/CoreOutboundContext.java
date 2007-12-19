package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.ContextService;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.FutureReply;
import java.util.concurrent.ConcurrentMap;

/**
 *
 */
public final class CoreOutboundContext<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundContext.class);

    private final CoreSession session;
    private final ContextIdentifier contextIdentifier;

    private final ConcurrentMap<Object, Object> contextMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<RequestIdentifier, CoreOutboundRequest<I, O>> requests = CollectionUtil.concurrentMap();
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.UP);
    private final Context<I, O> userContext = new UserContext();

    public CoreOutboundContext(final CoreSession session, final ContextIdentifier contextIdentifier) {
        this.session = session;
        this.contextIdentifier = contextIdentifier;
    }

    private enum State {
        UP,
        STOPPING,
        DOWN,
    }

    // Request management

    void dropRequest(final RequestIdentifier requestIdentifier, final CoreOutboundRequest<I, O> coreOutboundRequest) {
        requests.remove(requestIdentifier, coreOutboundRequest);
    }

    // Outbound protocol messages

    boolean sendCancelRequest(final RequestIdentifier requestIdentifier, final boolean mayInterrupt) {
        synchronized(state) {
            return state.in(State.UP) && session.sendCancelRequest(contextIdentifier, requestIdentifier, mayInterrupt);
        }
    }

    void sendRequest(final RequestIdentifier requestIdentifier, final Request<I> request) throws RemotingException {
        session.sendRequest(contextIdentifier, requestIdentifier, request);
    }

    // Inbound protocol messages

    @SuppressWarnings ({"unchecked"})
    void receiveCloseContext() {
        final CoreOutboundRequest[] requestArray;
        synchronized(state) {
            if (! state.transition(State.UP, State.STOPPING)) {
                return;
            }
            requestArray = requests.values().toArray(empty);
        }
        for (CoreOutboundRequest<I, O> request : requestArray) {
            request.receiveClose();
        }
        session.removeContext(contextIdentifier);
    }

    void receiveCancelAcknowledge(RequestIdentifier requestIdentifier) {
        final CoreOutboundRequest<I, O> request = requests.get(requestIdentifier);
        if (request != null) {
            request.receiveCancelAcknowledge();
        }
    }

    void receiveReply(RequestIdentifier requestIdentifier, Reply<O> reply) {
        final CoreOutboundRequest<I, O> request = requests.get(requestIdentifier);
        if (request != null) {
            request.receiveReply(reply);
        }
    }

    void receiveException(RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
        final CoreOutboundRequest<I, O> request = requests.get(requestIdentifier);
        if (request != null) {
            request.receiveException(exception);
        } else {
            log.trace("Received an exception for an unknown request (%s)", requestIdentifier);
        }
    }

    // Other protocol-related

    RequestIdentifier openRequest() throws RemotingException {
        return session.openRequest(contextIdentifier);
    }

    // Getters

    Context<I,O> getUserContext() {
        return userContext;
    }

    // Other mgmt

    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            receiveCloseContext();
            log.trace("Leaked a context instance: %s", this);
        }
    }

    public final class UserContext implements Context<I, O> {

        private UserContext() {
        }

        public void close() throws RemotingException {
            receiveCloseContext();
        }

        public Request<I> createRequest(final I body) {
            synchronized(state) {
                state.require(State.UP);
                return new RequestImpl<I>(body);
            }
        }

        public Reply<O> invoke(final Request<I> request) throws RemotingException, RemoteExecutionException, InterruptedException {
            return send(request).get();
        }

        public FutureReply<O> send(final Request<I> request) throws RemotingException {
            synchronized(state) {
                // todo: these tasks should be fast, but it may be worth exploring using a multi readers/single writer lock instead
                state.require(State.UP);
                final RequestIdentifier requestIdentifier;
                requestIdentifier = openRequest();
                final CoreOutboundRequest<I, O> outboundRequest = new CoreOutboundRequest<I, O>(CoreOutboundContext.this, requestIdentifier);
                requests.put(requestIdentifier, outboundRequest);
                // Request must be sent *after* the identifier is registered in the map
                sendRequest(requestIdentifier, request);
                return outboundRequest.getFutureReply();
            }
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return contextMap;
        }

        public <T extends ContextService> T getService(final Class<T> serviceType) throws RemotingException {
            // todo interceptors
            return null;
        }

        public <T extends ContextService> boolean hasService(final Class<T> serviceType) {
            // todo interceptors
            return false;
        }
    }

    private static final CoreOutboundRequest[] empty = new CoreOutboundRequest[0];
}

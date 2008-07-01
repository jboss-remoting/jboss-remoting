package org.jboss.cx.remoting.core;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.ClientContext;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ServiceContext;
import org.jboss.xnio.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;
import org.jboss.cx.remoting.util.CollectionUtil;
import static org.jboss.cx.remoting.util.CollectionUtil.synchronizedHashSet;

/**
 *
 */
public final class CoreInboundClient<I, O> {
    private static final Logger log = org.jboss.xnio.log.Logger.getLogger(CoreInboundClient.class);

    private final RequestListener<I, O> requestListener;
    private final Executor executor;
    private final ServiceContext serviceContext;
    private final Set<CoreInboundRequest<I, O>> requests = synchronizedHashSet();
    private final AtomicStateMachine<State> state = start(State.NEW);
    private final ClientContext clientContext = new UserClientContext();

    private ClientInitiator clientInitiator;
    private ConcurrentMap<Object, Object> attributes = CollectionUtil.concurrentMap();

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        NEW,
        UP,
        STOPPING,
        DOWN;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    public CoreInboundClient(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
        serviceContext = null;
    }

    public CoreInboundClient(final RequestListener<I, O> requestListener, final Executor executor, final ServiceContext serviceContext) {
        this.requestListener = requestListener;
        this.executor = executor;
        this.serviceContext = serviceContext;
    }

    public ClientResponder<I, O> getClientResponder() {
        return new Responder();
    }

    public void initialize(final ClientInitiator clientInitiator) {
        state.requireTransitionExclusive(State.NEW, State.UP);
        this.clientInitiator = clientInitiator;
        state.releaseDowngrade();
        try {
            if (requestListener != null) {
                requestListener.handleClientOpen(clientContext);
            }
        } finally {
            state.release();
        }
    }

    public void remove(final CoreInboundRequest<I, O> request) {
        final State current = state.getStateHold();
        try {
            requests.remove(request);
            if (current != State.STOPPING) {
                return;
            }
        } finally {
            state.release();
        }
        if (requests.isEmpty()) {
            state.transition(State.STOPPING, State.DOWN);
        }
    }

    // Accessors

    public ClientContext getClientContext() {
        return clientContext;
    }

    // Support classes

    public final class Responder implements ClientResponder<I, O> {
        private Responder() {
        }

        public RequestResponder<I> createNewRequest(final RequestInitiator<O> requestInitiator) throws RemotingException {
            if (state.inHold(State.UP)) try {
                final CoreInboundRequest<I, O> inboundRequest = new CoreInboundRequest<I, O>(requestListener, executor, clientContext);
                inboundRequest.initialize(requestInitiator);
                requests.add(inboundRequest);
                return inboundRequest.getRequestResponder();
            } finally {
                state.release();
            } else {
                throw new RemotingException("Client is not up");
            }
        }

        public void handleClose(final boolean immediate, final boolean cancel) throws RemotingException {
            if (state.transition(State.UP, State.STOPPING)) {
                clientInitiator.handleClosing(false);
                if (immediate || cancel) {
                    for (CoreInboundRequest<I, O> inboundRequest : requests) {
                        try {
                            inboundRequest.getRequestResponder().handleCancelRequest(immediate );
                        } catch (Exception e) {
                            log.trace("Failed to notify inbound request of cancellation upon context close: %s", e);
                        }
                    }
                }
            }
        }
    }

    public final class UserClientContext implements ClientContext {
        private UserClientContext() {
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return attributes;
        }

        public ServiceContext getServiceContext() {
            return serviceContext;
        }

        public void close() throws RemotingException {
            clientInitiator.handleClosing(false);
        }

        public void addCloseHandler(final CloseHandler<ClientContext> contextContextCloseHandler) {
        }
    }
}

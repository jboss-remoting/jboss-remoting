package org.jboss.cx.remoting.core;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.ContextContext;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ServiceContext;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;
import org.jboss.cx.remoting.util.CollectionUtil;
import static org.jboss.cx.remoting.util.CollectionUtil.synchronizedHashSet;

/**
 *
 */
public final class CoreInboundContext<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundContext.class);

    private final RequestListener<I, O> requestListener;
    private final Executor executor;
    private final ServiceContext serviceContext;
    private final Set<CoreInboundRequest<I, O>> requests = synchronizedHashSet();
    private final AtomicStateMachine<State> state = start(State.NEW);
    private final ContextContext contextContext = new UserContextContext();

    private ContextClient contextClient;
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

    public CoreInboundContext(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
        serviceContext = null;
    }

    public CoreInboundContext(final RequestListener<I, O> requestListener, final Executor executor, final ServiceContext serviceContext) {
        this.requestListener = requestListener;
        this.executor = executor;
        this.serviceContext = serviceContext;
    }

    public ContextServer<I, O> getContextServer() {
        return new Server();
    }

    public void initialize(final ContextClient contextClient) {
        state.requireTransitionExclusive(State.NEW, State.UP);
        this.contextClient = contextClient;
        state.releaseDowngrade();
        try {
            requestListener.handleContextOpen(contextContext);
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

    public ContextContext getContextContext() {
        return contextContext;
    }

    // Support classes

    public final class Server implements ContextServer<I, O> {
        private Server() {
        }

        public RequestServer<I> createNewRequest(final RequestClient<O> requestClient) throws RemotingException {
            if (state.inHold(State.UP)) try {
                final CoreInboundRequest<I, O> inboundRequest = new CoreInboundRequest<I, O>(requestListener, executor, contextContext);
                inboundRequest.initialize(requestClient);
                requests.add(inboundRequest);
                return inboundRequest.getRequester();
            } finally {
                state.release();
            } else {
                throw new RemotingException("Context is not up");
            }
        }

        public void handleClose(final boolean immediate, final boolean cancel, final boolean interrupt) throws RemotingException {
            if (state.transition(State.UP, State.STOPPING)) {
                contextClient.handleClosing(false);
                if (immediate || cancel) {
                    for (CoreInboundRequest<I, O> inboundRequest : requests) {
                        try {
                            inboundRequest.getRequester().handleCancelRequest(immediate || interrupt);
                        } catch (Exception e) {
                            log.trace("Failed to notify inbound request of cancellation upon context close: %s", e);
                        }
                    }
                }
            }
        }
    }

    public final class UserContextContext implements ContextContext {
        private UserContextContext() {
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return attributes;
        }

        public ServiceContext getServiceContext() {
            return serviceContext;
        }

        public void close() throws RemotingException {
            // todo
        }

        public void closeImmediate() throws RemotingException {
        }

        public void addCloseHandler(final CloseHandler<ContextContext> contextContextCloseHandler) {
        }
    }
}

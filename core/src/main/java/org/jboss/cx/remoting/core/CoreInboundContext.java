package org.jboss.cx.remoting.core;

import java.util.concurrent.Executor;
import java.util.Set;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;

import static org.jboss.cx.remoting.util.CollectionUtil.synchronizedHashSet;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;

/**
 *
 */
public final class CoreInboundContext<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundContext.class);

    private final RequestListener<I, O> requestListener;
    private final Executor executor;
    private final Set<CoreInboundRequest<I, O>> requests = synchronizedHashSet();
    private final AtomicStateMachine<State> state = start(State.NEW);

    private ContextClient contextClient;

    private enum State {
        NEW,
        UP,
        STOPPING,
        DOWN,
    }

    public CoreInboundContext(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
    }

    public ContextServer<I, O> getContextServer() {
        return new Server();
    }

    public void initialize(final ContextClient contextClient) {
        state.requireTransitionExclusive(State.NEW, State.UP);
        this.contextClient = contextClient;
        state.releaseExclusive();
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

    public final class Server implements ContextServer<I, O> {
        public RequestServer<I> createNewRequest(final RequestClient<O> requestClient) throws RemotingException {
            if (state.inHold(State.UP)) try {
                final CoreInboundRequest<I, O> inboundRequest = new CoreInboundRequest<I, O>(requestListener, executor);
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
}

package org.jboss.cx.remoting.core;

import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;

/**
 *
 */
public final class CoreInboundService<I, O> {
    private final RequestListener<I, O> requestListener;
    private final Executor executor;
    private ServiceClient serviceClient;

    private final AtomicStateMachine<State> state = start(State.INITIAL);

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    public CoreInboundService(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
    }

    public void initialize(final ServiceClient serviceClient) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.serviceClient = serviceClient;
        state.releaseExclusive();
    }

    public ServiceServer<I, O> getServiceServer() {
        return new ServiceServer<I, O>() {
            public void handleClose() throws RemotingException {
                // todo - prevent new context creation?
            }

            public ContextServer<I, O> createNewContext(final ContextClient client) {
                final CoreInboundContext<I, O> context = new CoreInboundContext<I, O>(requestListener, executor);
                context.initialize(client);
                return context.getContextServer();
            }
        };
    }
}

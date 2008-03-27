package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.State;
import org.jboss.cx.remoting.log.Logger;
import java.util.concurrent.Executor;
import java.io.Serializable;

/**
 *
 */
public final class CoreOutboundService<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundService.class);

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final ContextSource<I, O> userContextSource = new UserContextSource();
    private final ServiceClient serviceClient = new ServiceClientImpl();
    private final Executor executor;

    private ServiceServer<I,O> serviceServer;

    public CoreOutboundService(final Executor executor) {
        this.executor = executor;
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP,
        CLOSING,
        DOWN;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    // Getters

    ContextSource<I, O> getUserContextSource() {
        return userContextSource;
    }

    public ServiceClient getServiceClient() {
        return serviceClient;
    }

    public void initialize(final ServiceServer<I, O> serviceServer) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.serviceServer = serviceServer;
        state.releaseExclusive();
    }

    @SuppressWarnings ({"SerializableInnerClassWithNonSerializableOuterClass"})
    public final class UserContextSource extends AbstractRealContextSource<I, O> {
        protected UserContextSource() {
            super(serviceServer);
        }

        public void close() {
            // todo ...
        }

        public void closeImmediate() throws RemotingException {
            // todo ...
        }

        public void addCloseHandler(final CloseHandler<ContextSource<I, O>> closeHandler) {
            // todo ...
        }

        public Context<I, O> createContext() throws RemotingException {
            final CoreOutboundContext<I, O> context = new CoreOutboundContext<I, O>(executor);
            final ContextServer<I, O> contextServer = serviceServer.createNewContext(context.getContextClient());
            context.initialize(contextServer);
            return context.getUserContext();
        }
    }

    public final class ServiceClientImpl implements ServiceClient {
        public void handleClosing() throws RemotingException {
            // todo - remote side is closing
        }
    }
}

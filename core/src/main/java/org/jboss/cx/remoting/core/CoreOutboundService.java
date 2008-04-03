package org.jboss.cx.remoting.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.CollectionUtil;

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
    private Set<CloseHandler<ContextSource<I,O>>> closeHandlers = CollectionUtil.synchronizedSet(new LinkedHashSet<CloseHandler<ContextSource<I, O>>>());

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

        private void doClose() throws RemotingException {
            state.waitForNot(State.INITIAL);
            if (state.transitionHold(State.UP, State.DOWN)) try {
                synchronized (closeHandlers) {
                    for (final CloseHandler<ContextSource<I, O>> handler : closeHandlers) {
                        executor.execute(new Runnable() {
                            public void run() {
                                handler.handleClose(UserContextSource.this);
                            }
                        });
                    }
                    closeHandlers.clear();
                }
                serviceServer.handleClose();
            } finally {
                state.release();
            }
        }

        public void close() throws RemotingException {
            doClose();
        }

        public void closeImmediate() throws RemotingException {
            doClose();
        }

        public void addCloseHandler(final CloseHandler<ContextSource<I, O>> closeHandler) {
            final State current = state.getStateHold();
            try {
                switch (current) {
                    case DOWN:
                        closeHandler.handleClose(this);
                        break;
                    default:
                        closeHandlers.add(closeHandler);
                        break;
                }
            } finally {
                state.release();
            }
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

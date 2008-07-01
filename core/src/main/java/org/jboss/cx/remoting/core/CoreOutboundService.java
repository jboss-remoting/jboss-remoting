package org.jboss.cx.remoting.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.ClientSource;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.xnio.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class CoreOutboundService<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundService.class);

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final ClientSource<I, O> userClientSource = new UserClientSource();
    private final ServiceInitiator serviceInitiator = new ServiceInitiatorImpl();
    private final Executor executor;

    private ServiceResponder<I,O> serviceResponder;
    private Set<CloseHandler<ClientSource<I,O>>> closeHandlers = CollectionUtil.synchronizedSet(new LinkedHashSet<CloseHandler<ClientSource<I, O>>>());

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

    ClientSource<I, O> getUserContextSource() {
        return userClientSource;
    }

    public ServiceInitiator getServiceClient() {
        return serviceInitiator;
    }

    public void initialize(final ServiceResponder<I, O> serviceResponder) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.serviceResponder = serviceResponder;
        state.releaseExclusive();
    }

    @SuppressWarnings ({"SerializableInnerClassWithNonSerializableOuterClass"})
    public final class UserClientSource extends AbstractRealClientSource<I, O> {
        protected UserClientSource() {
            super(serviceResponder);
        }

        private void doClose() throws RemotingException {
            state.waitForNot(State.INITIAL);
            if (state.transitionHold(State.UP, State.DOWN)) try {
                synchronized (closeHandlers) {
                    for (final CloseHandler<ClientSource<I, O>> handler : closeHandlers) {
                        executor.execute(new Runnable() {
                            public void run() {
                                handler.handleClose(UserClientSource.this);
                            }
                        });
                    }
                    closeHandlers.clear();
                }
                serviceResponder.handleClose();
            } finally {
                state.release();
            }
        }

        public void close() throws RemotingException {
            doClose();
        }

        public void addCloseHandler(final CloseHandler<ClientSource<I, O>> closeHandler) {
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

        public Client<I, O> createContext() throws RemotingException {
            final CoreOutboundClient<I, O> client = new CoreOutboundClient<I, O>(executor);
            final ClientResponder<I, O> clientResponder = serviceResponder.createNewClient(client.getClientInitiator());
            client.initialize(clientResponder);
            return client.getUserContext();
        }
    }

    public final class ServiceInitiatorImpl implements ServiceInitiator {
        public void handleClosing() throws RemotingException {
            // todo - remote side is closing
        }
    }
}

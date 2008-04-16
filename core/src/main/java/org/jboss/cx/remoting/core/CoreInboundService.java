package org.jboss.cx.remoting.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ServiceContext;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import static org.jboss.cx.remoting.util.AtomicStateMachine.start;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class CoreInboundService<I, O> {

    private final RequestListener<I, O> requestListener;
    private final Executor executor;

    private final ServiceContext serviceContext = new UserServiceContext();
    private final AtomicStateMachine<State> state = start(State.INITIAL);
    private final ConcurrentMap<Object, Object> attributes = CollectionUtil.concurrentMap();

    private ServiceInitiator serviceInitiator;
    private final Set<CloseHandler<ServiceContext>> closeHandlers = CollectionUtil.synchronizedSet(new LinkedHashSet<CloseHandler<ServiceContext>>());

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP,
        DOWN;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    public CoreInboundService(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
    }

    public void initialize(final ServiceInitiator serviceInitiator) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.serviceInitiator = serviceInitiator;
        state.releaseDowngrade();
        try {
            requestListener.handleServiceOpen(serviceContext);
        } finally {
            state.release();
        }
    }

    private void doClose() {
        if (state.transition(State.DOWN)) {
            synchronized (closeHandlers) {
                for (final CloseHandler<ServiceContext> closeHandler : closeHandlers) {
                    executor.execute(new Runnable() {
                        public void run() {
                            closeHandler.handleClose(serviceContext);
                        }
                    });
                }
                closeHandlers.clear();
            }
        }
    }

    public ServiceResponder<I, O> getServiceResponder() {
        return new ServiceResponder<I, O>() {
            public void handleClose() throws RemotingException {
                doClose();
            }

            public ClientResponder<I, O> createNewClient(final ClientInitiator clientInitiator) {
                final CoreInboundClient<I, O> client = new CoreInboundClient<I, O>(requestListener, executor, serviceContext);
                client.initialize(clientInitiator);
                return client.getClientResponder();
            }
        };
    }

    public final class UserServiceContext implements ServiceContext {
        private UserServiceContext() {
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return attributes;
        }

        public void close() throws RemotingException {
            doClose();
            serviceInitiator.handleClosing();
        }

        public void closeImmediate() throws RemotingException {
            doClose();
            serviceInitiator.handleClosing();
        }

        public void addCloseHandler(final CloseHandler<ServiceContext> closeHandler) {
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
    }
}

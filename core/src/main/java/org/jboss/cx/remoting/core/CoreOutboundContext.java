package org.jboss.cx.remoting.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.core.util.QueueExecutor;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class CoreOutboundContext<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundContext.class);

    private final ConcurrentMap<Object, Object> contextMap = CollectionUtil.concurrentMap();
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final ContextClient contextClient = new ContextClientImpl();
    private final Set<CloseHandler<Context<I, O>>> closeHandlers = CollectionUtil.synchronizedSet(new LinkedHashSet<CloseHandler<Context<I, O>>>());
    private final Executor executor;

    private Context<I, O> userContext;
    private ContextServer<I, O> contextServer;
    
    public CoreOutboundContext(final Executor executor) {
        this.executor = executor;
    }

    public void initialize(final ContextServer<I, O> contextServer) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.contextServer = contextServer;
        userContext = new UserContext();
        state.releaseExclusive();
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UP,
        STOPPING,
        DOWN,;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    // Getters

    Context<I,O> getUserContext() {
        return userContext;
    }

    ContextClient getContextClient() {
        return contextClient;
    }

    // Other mgmt

    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            // todo close it
            log.trace("Leaked a context instance: %s", this);
        }
    }

    public final class UserContext extends AbstractRealContext<I, O> {

        private UserContext() {
            super(contextServer);
        }

        private void doClose(final boolean immediate, final boolean cancel) throws RemotingException {
            state.waitForNot(State.INITIAL);
            if (state.transitionHold(State.UP, State.STOPPING)) try {
                synchronized (closeHandlers) {
                    for (final CloseHandler<Context<I, O>> handler : closeHandlers) {
                        executor.execute(new Runnable() {
                            public void run() {
                                handler.handleClose(UserContext.this);
                            }
                        });
                    }
                    closeHandlers.clear();
                }
                contextServer.handleClose(immediate, cancel);
            } finally {
                state.release();
            }
        }

        public void close() throws RemotingException {
            doClose(false, false);
        }

        public void closeImmediate() throws RemotingException {
            doClose(true, true);
        }

        public void addCloseHandler(final CloseHandler<Context<I, O>> closeHandler) {
            final State current = state.getStateHold();
            try {
                switch (current) {
                    case STOPPING:
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

        public O invoke(final I request) throws RemotingException, RemoteExecutionException {
            state.requireHold(State.UP);
            try {
                final QueueExecutor queueExecutor = new QueueExecutor();
                final CoreOutboundRequest<I, O> outboundRequest = new CoreOutboundRequest<I, O>();
                final RequestServer<I> requestTerminus = contextServer.createNewRequest(outboundRequest.getReplier());
                outboundRequest.setRequester(requestTerminus);
                requestTerminus.handleRequest(request, queueExecutor);
                final FutureReply<O> futureReply = outboundRequest.getFutureReply();
                futureReply.addCompletionNotifier(new RequestCompletionHandler<O>() {
                    public void notifyComplete(final FutureReply<O> futureReply) {
                        queueExecutor.shutdown();
                    }
                });
                queueExecutor.runQueue();
                return futureReply.get();
            } finally {
                state.release();
            }
        }

        public FutureReply<O> send(final I request) throws RemotingException {
            state.requireHold(State.UP);
            try {
                final CoreOutboundRequest<I, O> outboundRequest = new CoreOutboundRequest<I, O>();
                final RequestServer<I> requestTerminus = contextServer.createNewRequest(outboundRequest.getReplier());
                outboundRequest.setRequester(requestTerminus);
                requestTerminus.handleRequest(request, executor);
                return outboundRequest.getFutureReply();
            } finally {
                state.release();
            }
        }

        public void sendOneWay(final I request) throws RemotingException {
            state.requireHold(State.UP);
            try {
                final RequestServer<I> requestServer = contextServer.createNewRequest(null);
                requestServer.handleRequest(request, executor);
            } finally {
                state.release();
            }
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return contextMap;
        }
    }

    public final class ContextClientImpl implements ContextClient {
        public void handleClosing(boolean done) throws RemotingException {
            // todo - remote side is closing
        }
    }
}

package org.jboss.cx.remoting.core;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.io.Serializable;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.CloseHandler;
import org.jboss.cx.remoting.core.util.QueueExecutor;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.log.Logger;

/**
 *
 */
public final class CoreOutboundContext<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundContext.class);

    private final ConcurrentMap<Object, Object> contextMap = CollectionUtil.concurrentMap();
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final Context<I, O> userContext = new UserContext();
    private final ContextClient contextClient = new ContextClientImpl();
    private final Executor executor;

    private ContextServer<I, O> contextServer;
    
    public CoreOutboundContext(final Executor executor) {
        this.executor = executor;
    }

    public void initialize(final ContextServer<I, O> contextServer) {
        state.requireTransitionExclusive(State.INITIAL, State.UP);
        this.contextServer = contextServer;
        state.releaseExclusive();
    }

    private enum State {
        INITIAL,
        UP,
        STOPPING,
        DOWN,
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

    @SuppressWarnings ({"SerializableInnerClassWithNonSerializableOuterClass"})
    public final class UserContext implements Context<I, O>, Serializable {
        private static final long serialVersionUID = 1L;

        private UserContext() {
        }

        private Object writeReplace() {
            return contextServer;
        }

        public void close() throws RemotingException {
            contextServer.handleClose(false, false, false);
        }

        public void closeCancelling(final boolean mayInterrupt) throws RemotingException {
            contextServer.handleClose(false, true, mayInterrupt);
        }

        public void closeImmediate() throws RemotingException {
            contextServer.handleClose(true, true, true);
        }

        public void addCloseHandler(final CloseHandler<Context<I, O>> closeHandler) {
            // todo ...
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

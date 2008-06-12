package org.jboss.cx.remoting.core;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.IndeterminateOutcomeException;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;

/**
 *
 */
public final class CoreOutboundRequest<I, O> {

    private static final Logger log = Logger.getLogger(CoreOutboundRequest.class);

    private RequestResponder<I> requestResponder;

    private final RequestInitiator<O> requestInitiator = new RequestInitiatorImpl();
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.WAITING);
    private final FutureReply<O> futureReply = new FutureReplyImpl();

    /* Protected by {@code state} */
    private O reply;
    /* Protected by {@code state} */
    private RemoteExecutionException exception;
    /* Protected by {@code state} */
    private List<RequestCompletionHandler<O>> handlers = Collections.synchronizedList(new LinkedList<RequestCompletionHandler<O>>());

    public CoreOutboundRequest() {
    }

    public RequestResponder<I> getRequester() {
        return requestResponder;
    }

    public void setRequestResponder(final RequestResponder<I> requestResponder) {
        this.requestResponder = requestResponder;
    }

    public FutureReply<O> getFutureReply() {
        return futureReply;
    }

    public RequestInitiator<O> getReplier() {
        return requestInitiator;
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        WAITING,
        DONE,
        EXCEPTION,
        CANCELLED,
        TERMINATED,;

        public boolean isReachable(final State dest) {
            switch (this) {
                case WAITING:
                case DONE:
                    return compareTo(dest) < 0;
                default:
                    return false;
            }
        }
    }

    /**
     * Complete the request.  Call only with the monitor held, not in WAITING state.
     */
    private void complete() {
        final List<RequestCompletionHandler<O>> handlers = this.handlers;
        if (handlers != null) {
            this.handlers = null;
            final Iterator<RequestCompletionHandler<O>> iterator = handlers.iterator();
            while (iterator.hasNext()) {
                final RequestCompletionHandler<O> handler = iterator.next();
                try {
                    handler.notifyComplete(futureReply);
                } catch (Throwable t) {
                    log.trace(t, "Request completion notifier failed for notifier object %s", String.valueOf(handler));
                }
                iterator.remove();
            }
        }
    }

    public final class RequestInitiatorImpl implements RequestInitiator<O> {
        public void handleCancelAcknowledge() {
            if (state.transitionHold(State.WAITING, State.CANCELLED)) try {
                complete();
            } finally {
                state.release();
            }
        }

        public void handleReply(final O reply) {
            if (state.transitionExclusive(State.WAITING, State.DONE)) try {
                CoreOutboundRequest.this.reply = reply;
            } finally {
                state.releaseDowngrade();
                try {
                    complete();
                } finally {
                    state.release();
                }
            }
        }

        public void handleException(final RemoteExecutionException exception) {
            if (state.transitionExclusive(State.WAITING, State.EXCEPTION)) try {
                CoreOutboundRequest.this.exception = exception;
            } finally {
                state.releaseDowngrade();
                try {
                    complete();
                } finally {
                    state.release();
                }
            }
        }
    }

    public final class FutureReplyImpl implements FutureReply<O> {

        private FutureReplyImpl() {
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            if (state.inHold(State.WAITING)) try {
                try {
                    requestResponder.handleCancelRequest(mayInterruptIfRunning);
                } catch (RemotingException e) {
                    return false;
                }
            } finally {
                state.release();
            }
            return state.waitForNot(State.WAITING) == State.CANCELLED;
        }

        public FutureReply<O> sendCancel(final boolean mayInterruptIfRunning) {
            if (state.inHold(State.WAITING)) try {
                try {
                    requestResponder.handleCancelRequest(mayInterruptIfRunning);
                } catch (RemotingException e) {
                    // do nothing
                }
            } finally {
                state.release();
            }
            return this;
        }

        public boolean isCancelled() {
            return state.in(State.CANCELLED);
        }

        public boolean isDone() {
            return state.in(State.DONE);
        }

        public O get() throws CancellationException, RemoteExecutionException {
            final State newState = state.waitForNotHold(State.WAITING);
            try {
                switch(newState) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case TERMINATED:
                        throw new IndeterminateOutcomeException("Request terminated abruptly; outcome unknown");
                    default:
                        throw new IllegalStateException("Wrong state");
                }
            } finally {
                state.release();
            }
        }

        public O getInterruptibly() throws InterruptedException, CancellationException, RemoteExecutionException {
            final State newState = state.waitInterruptiblyForNotHold(State.WAITING);
            try {
                switch(newState) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case TERMINATED:
                        throw new IndeterminateOutcomeException("Request terminated abruptly; outcome unknown");
                    default:
                        throw new IllegalStateException("Wrong state");
                }
            } finally {
                state.release();
            }
        }

        public O get(long timeout, TimeUnit unit) throws CancellationException, RemoteExecutionException {
            final State newState = state.waitForNotHold(State.WAITING, timeout, unit);
            try {
                switch (newState) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case TERMINATED:
                        throw new IndeterminateOutcomeException("Request terminated abruptly; outcome unknown");
                }
                throw new IllegalStateException("Wrong state");
            } finally {
                state.release();
            }
        }

        public O getInterruptibly(final long timeout, final TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException {
            final State newState = state.waitInterruptiblyForNotHold(State.WAITING, timeout, unit);
            try {
                switch (newState) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case TERMINATED:
                        throw new IndeterminateOutcomeException("Request terminated abruptly; outcome unknown");
                }
                throw new IllegalStateException("Wrong state");
            } finally {
                state.release();
            }
        }

        public FutureReply<O> addCompletionNotifier(RequestCompletionHandler<O> handler) {
            final State currentState = state.getStateHold();
            try {
                switch (currentState) {
                    case CANCELLED:
                    case DONE:
                    case EXCEPTION:
                        handler.notifyComplete(this);
                        break;
                    case WAITING:
                        handlers.add(handler);
                        break;
                }
            } finally {
                state.release();
            }
            return this;
        }
    }
}

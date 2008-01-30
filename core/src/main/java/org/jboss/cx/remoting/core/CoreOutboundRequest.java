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
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.core.util.AtomicStateMachine;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class CoreOutboundRequest<I, O> {

    private static final Logger log = Logger.getLogger(CoreOutboundRequest.class);

    private final CoreOutboundContext<I, O> context;
    private final RequestIdentifier requestIdentifier;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.WAITING);
    private final FutureReply<O> futureReply = new FutureReplyImpl();

    /* Protected by {@code state} */
    private O reply;
    /* Protected by {@code state} */
    private RemoteExecutionException exception;
    /* Protected by {@code state} */
    private List<RequestCompletionHandler<O>> handlers = Collections.synchronizedList(new LinkedList<RequestCompletionHandler<O>>());

    public CoreOutboundRequest(final CoreOutboundContext<I, O> context, final RequestIdentifier requestIdentifier) {
        this.context = context;
        this.requestIdentifier = requestIdentifier;
    }

    public FutureReply<O> getFutureReply() {
        return futureReply;
    }

    private enum State {
        WAITING,
        DONE,
        EXCEPTION,
        CANCELLED,
        TERMINATED,
    }

    /**
     * Complete the request.  Call only with the monitor held, not in WAITING state.
     */
    private void complete() {
        drop();
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

    private void drop() {
        context.dropRequest(requestIdentifier, this);
    }

    // Incoming protocol messages

    /**
     * Request was possibly abruptly terminated.
     */
    void receiveClose() {
        if (state.transitionHold(State.WAITING, State.TERMINATED)) try {
            drop();
        } finally {
            state.release();
        }
    }

    /**
     * Receive a cancel acknowledge for this request.
     */
    void receiveCancelAcknowledge() {
        state.requireTransitionHold(State.WAITING, State.CANCELLED);
        try {
            complete();
        } finally {
            state.release();
        }
    }

    /**
     * Receive a reply for this request.
     *
     * @param reply the reply
     */
    void receiveReply(final O reply) {
        state.requireTransitionExclusive(State.WAITING, State.DONE);
        this.reply = reply;
        state.releaseDowngrade();
        try {
            complete();
        } finally {
            state.release();
        }
    }

    /**
     * Receive an exception for this request.
     *
     * @param exception the exception
     */
    void receiveException(final RemoteExecutionException exception) {
        state.requireTransitionExclusive(State.WAITING, State.EXCEPTION);
        this.exception = exception;
        state.releaseDowngrade();
        try {
            complete();
        } finally {
            state.release();
        }
    }

    public final class FutureReplyImpl implements FutureReply<O> {

        private FutureReplyImpl() {
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            if (state.inHold(State.WAITING)) try {
                if (! context.sendCancelRequest(requestIdentifier, mayInterruptIfRunning)) {
                    // the cancel request could not be sent at all
                    return false;
                }
            } finally {
                state.release();
            }
            return state.waitForNot(State.WAITING) == State.CANCELLED;
        }

        public boolean isCancelled() {
            return state.in(State.CANCELLED);
        }

        public boolean isDone() {
            return state.in(State.DONE);
        }

        public O get() throws InterruptedException, CancellationException, RemoteExecutionException {
            final State newState = state.waitInterruptablyForNotHold(State.WAITING);
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

        public O get(long timeout, TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException {
            final State newState = state.waitInterruptablyForNotHold(State.WAITING, timeout, unit);
            try {
                switch (newState) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case WAITING:
                        return null;
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

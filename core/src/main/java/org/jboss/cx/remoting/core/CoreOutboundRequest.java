package org.jboss.cx.remoting.core;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.core.util.Logger;
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
    private Reply<O> reply;
    /* Protected by {@code state} */
    private RemoteExecutionException exception;
    /* Protected by {@code state} */
    private List<RequestCompletionHandler<O>> handlers;

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
        synchronized(state) {
            if (state.transition(State.WAITING, State.TERMINATED)) {
                drop();
            }
        }
    }

    /**
     * Receive a cancel acknowledge for this request.
     */
    void receiveCancelAcknowledge() {
        synchronized(state) {
            if (state.transition(State.WAITING, State.CANCELLED)) {
                complete();
            } else {
                throw new IllegalStateException("Got cancel acknowledge from state " + state.getState());
            }
        }
    }

    /**
     * Receive a reply for this request.
     *
     * @param reply the reply
     */
    void receiveReply(final Reply<O> reply) {
        synchronized(state) {
            if (state.transition(State.WAITING, State.DONE)) {
                this.reply = reply;
                complete();
            } else {
                throw new IllegalStateException("Got reply from state " + state.getState());
            }
        }
    }

    /**
     * Receive an exception for this request.
     *
     * @param exception the exception
     */
    void receiveException(final RemoteExecutionException exception) {
        synchronized(state) {
            if (state.transition(State.WAITING, State.EXCEPTION)) {
                this.exception = exception;
                complete();
            } else {
                throw new IllegalStateException("Got exception from state " + state.getState());
            }
        }
    }

    public final class FutureReplyImpl implements FutureReply<O> {

        private FutureReplyImpl() {
        }

        public boolean cancel(final boolean mayInterruptIfRunning) {
            synchronized(state) {
                if (state.in(State.WAITING)) {
                    if (! context.sendCancelRequest(requestIdentifier, mayInterruptIfRunning)) {
                        // the cancel request could not be sent at all
                        return false;
                    }
                }
                return state.waitForNot(State.WAITING) == State.CANCELLED;
            }
        }

        public boolean isCancelled() {
            return state.in(State.CANCELLED);
        }

        public boolean isDone() {
            return state.in(State.DONE);
        }

        public Reply<O> get() throws InterruptedException, CancellationException, RemoteExecutionException {
            synchronized(state) {
                switch (state.waitInterruptablyForNot(State.WAITING)) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case TERMINATED:
                        // todo - maybe we need a specific exception type
                        throw new RemoteExecutionException("Request terminated; outcome unknown");
                }
                throw new IllegalStateException("Wrong state");
            }
        }

        public Reply<O> get(long timeout, TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException {
            synchronized(state) {
                switch (state.waitInterruptablyForNot(State.WAITING, timeout, unit)) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case WAITING:
                        return null;
                    case TERMINATED:
                        // todo - maybe we need a specific exception type
                        throw new RemoteExecutionException("Request terminated; outcome unknown");
                }
                throw new IllegalStateException("Wrong state");
            }
        }

        public FutureReply<O> setCompletionNotifier(RequestCompletionHandler<O> handler) {
            synchronized(state) {
                switch (state.getState()) {
                    case CANCELLED:
                    case DONE:
                    case EXCEPTION:
                        handler.notifyComplete(this);
                        break;
                    case WAITING:
                        if (handlers == null) {
                            handlers = new LinkedList<RequestCompletionHandler<O>>();
                        }
                        handlers.add(handler);
                        break;
                }
            }
            return this;
        }
    }
}

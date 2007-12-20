package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.core.util.AtomicStateMachine;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class CoreInboundRequest<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundRequest.class);

    private final RequestIdentifier requestIdentifier;
    private final Request<I> request;
    private final CoreInboundContext<I, O> context;
    private final RequestListener<I,O> requestListener;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final UserRequestContext userRequestContext = new UserRequestContext();

    public CoreInboundRequest(final RequestIdentifier requestIdentifier, final Request<I> request, final CoreInboundContext<I, O> context, final RequestListener<I, O> requestListener) {
        this.requestIdentifier = requestIdentifier;
        this.request = request;
        this.context = context;
        this.requestListener = requestListener;
    }

    private enum State {
        INITIAL,
        UNSENT,
        SENT,
    }

    void receiveRequest(final Request<I> request) {
        try {
            state.requireTransition(State.INITIAL, State.UNSENT);
            requestListener.handleRequest(userRequestContext, request);
        } catch (Throwable e) {
            if (state.transition(State.UNSENT, State.SENT)) {
                try {
                    if (e instanceof RemoteExecutionException) {
                        sendException((RemoteExecutionException) e);
                    } else {
                        sendException(new RemoteExecutionException("Execution failed: " + e.toString()));
                    }
                } catch (RemotingException e1) {
                    log.trace("Tried and failed to send an exception (%s) for a request (%s): %s", e, requestIdentifier, e1);
                }
            }
        }
    }

    void sendException(final RemoteExecutionException rex) throws RemotingException {
        context.sendException(requestIdentifier, rex);
    }

    public void receiveCancelRequest(final boolean mayInterrupt) {
    }

    public final class UserRequestContext implements RequestContext<O> {

        public boolean isCancelled() {
            // todo...
            return false;
        }

        public Reply<O> createReply(final O body) {
            return new ReplyImpl<O>(body);
        }

        public void sendReply(final Reply<O> reply) throws RemotingException, IllegalStateException {
            if (reply == null) {
                throw new NullPointerException("reply is null");
            }
            state.requireTransition(State.UNSENT, State.SENT);
            context.sendReply(requestIdentifier, reply);
        }

        public void sendFailure(final String msg, final Throwable cause) throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            final RemoteExecutionException rex = new RemoteExecutionException(msg, cause);
            rex.setStackTrace(cause.getStackTrace());
            sendException(rex);
        }

        public void sendCancelled() throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            context.sendCancelAcknowledge(requestIdentifier);
        }

        public void setCancelHandler(final RequestCancelHandler<O> requestCancelHandler) {
            // todo - should be a list
        }
    }
}

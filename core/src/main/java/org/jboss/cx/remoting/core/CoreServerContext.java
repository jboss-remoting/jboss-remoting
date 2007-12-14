package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RequestListener;
import static org.jboss.cx.remoting.core.AtomicStateMachine.start;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.spi.AbstractServerInterceptor;
import org.jboss.cx.remoting.spi.InterceptorContext;
import org.jboss.cx.remoting.spi.ServerInterceptorFactory;
import org.jboss.cx.remoting.spi.ServerInterceptor;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class CoreServerContext<I, O> {

    private final ContextIdentifier contextIdentifier;
    private final ProtocolHandler protocolHandler;
    private final RequestListener<I, O> requestListener;

    public CoreServerContext(final ContextIdentifier contextIdentifier, final ProtocolHandler protocolHandler, final RequestListener<I, O> requestListener, final List<ServerInterceptorFactory> factoryList) {
        this.contextIdentifier = contextIdentifier;
        this.protocolHandler = protocolHandler;
        this.requestListener = requestListener;
        firstInterceptor = new FirstInterceptor();
        ServerInterceptor prev = firstInterceptor;
        for (ServerInterceptorFactory factory : factoryList) {
            final ServerInterceptor current = factory.createInstance(contextIdentifier);
            current.setPrevious(prev);
            prev.setNext(current);
            prev = current;
        }
        lastInterceptor = new LastInterceptor();
        lastInterceptor.setPrevious(prev);
        prev.setNext(lastInterceptor);
    }

    private enum ReceivedRequestState {
        RUNNING,
        CANCEL_SENT,
        REPLY_SENT,
        EXCEPTION_SENT,
    }

    private final FirstInterceptor firstInterceptor;
    private final LastInterceptor lastInterceptor;

    private final ConcurrentMap<RequestIdentifier, ReceivedRequest> requests = CollectionUtil.concurrentMap();

    public ServerInterceptor getLastInterceptor() {
        return lastInterceptor;
    }

    protected void shutdown() {

    }

    private final class FirstInterceptor extends AbstractServerInterceptor {
        public void processInboundCancelRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final boolean mayInterruptIfRunning) {
            requests.get(requestIdentifier).receiveCancelRequest(mayInterruptIfRunning);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Request<?> request) {
            final ReceivedRequest receivedRequest = new ReceivedRequest(requestIdentifier);
            final ReceivedRequest oldEntry = requests.putIfAbsent(requestIdentifier, receivedRequest);
            if (oldEntry != null) {
                // todo log duplicate
                processOutboundException(context, requestIdentifier, new RemoteExecutionException("Duplicate request received; discarding"));
                requests.remove(requestIdentifier);
            } else {
                try {
                    requestListener.handleRequest(receivedRequest.requestContext, (Request<I>) request);
                } catch (RemoteExecutionException e) {
                    try {
                        receivedRequest.requestContext.sendFailure(e.getMessage(), e.getCause());
                    } catch (RemotingException e1) {
                        // todo log problem
                    }
                } catch (InterruptedException e) {
                    try {
                        receivedRequest.requestContext.sendCancelled();
                    } catch (RemotingException e1) {
                        // todo log problem
                    }
                }
            }
        }
    }

    private final class LastInterceptor extends AbstractServerInterceptor {
        public void processOutboundCancelAcknowledge(final InterceptorContext context, final RequestIdentifier requestIdentifier) {
            try {
                protocolHandler.sendCancelAcknowledge(contextIdentifier, requestIdentifier);
            } catch (IOException e) {
                // todo log failure
            }
        }

        public void processOutboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply<?> reply) {
            try {
                protocolHandler.sendReply(contextIdentifier, requestIdentifier, reply);
            } catch (IOException e) {
                // todo log failure
            }
        }

        public void processOutboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
            try {
                protocolHandler.sendException(contextIdentifier, requestIdentifier, exception);
            } catch (IOException e) {
                // todo log failure
            }
        }
    }

    public final class ReceivedRequest {
        private final RequestContext<O> requestContext = new UserRequestContext();
        private final AtomicStateMachine<ReceivedRequestState> state = start(ReceivedRequestState.RUNNING);
        private final RequestIdentifier requestIdentifier;

        /* Protected by {@code state} */
        private boolean cancelReceived;
        /* Protected by {@code state} */
        private RequestCancelHandler<O> handler;

        private ReceivedRequest(final RequestIdentifier requestIdentifier) {
            this.requestIdentifier = requestIdentifier;
        }

        public void receiveCancelRequest(boolean mayInterrupt) {
            synchronized (state) {
                if (state.in(ReceivedRequestState.RUNNING)) {
                    if (!cancelReceived) {
                        cancelReceived = true;
                        final RequestCancelHandler<O> handler = this.handler;
                        if (handler != null) {
                            this.handler = null;
                            handler.notifyCancel(requestContext);
                        }
                    }
                }
            }
        }

        public final class UserRequestContext implements RequestContext<O> {

            private UserRequestContext() {
            }

            public boolean isCancelled() {
                synchronized (state) {
                    return cancelReceived;
                }
            }

            public Reply<O> createReply(O body) {
                return new ReplyImpl<O>(body);
            }

            public void sendReply(Reply<O> reply) throws RemotingException, IllegalStateException {
                state.requireTransition(ReceivedRequestState.RUNNING, ReceivedRequestState.REPLY_SENT);
                firstInterceptor.processOutboundReply(null, requestIdentifier, reply);
            }

            public void sendFailure(String msg, Throwable cause) throws RemotingException, IllegalStateException {
                state.requireTransition(ReceivedRequestState.RUNNING, ReceivedRequestState.EXCEPTION_SENT);
                firstInterceptor.processOutboundException(null, requestIdentifier, new RemoteExecutionException(msg, cause));
            }

            public void sendCancelled() throws RemotingException, IllegalStateException {
                state.requireTransition(ReceivedRequestState.RUNNING, ReceivedRequestState.CANCEL_SENT);
                firstInterceptor.processOutboundCancelAcknowledge(null, requestIdentifier);
            }

            public void setCancelHandler(final RequestCancelHandler<O> newHandler) {
                synchronized (state) {
                    switch (state.getState()) {
                        case CANCEL_SENT:
                            throw new IllegalStateException("Cancel already sent");
                        case REPLY_SENT:
                            throw new IllegalStateException("Reply already sent");
                        case RUNNING:
                            if (cancelReceived) {
                                newHandler.notifyCancel(this);
                            } else {
                                handler = newHandler;
                            }
                    }
                }
            }
        }
    }
}

package org.jboss.cx.remoting.core;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.ClientContext;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.AtomicStateMachine;

/**
 *
 */
public final class CoreInboundRequest<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundRequest.class);

    private final RequestListener<I,O> requestListener;
    private final Executor executor;
    private final ClientContext clientContext;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final UserRequestContext userRequestContext = new UserRequestContext();
    private final RequestResponder<I> request = new Request();

    private RequestInitiator<O> requestInitiator;

    /**
     * @protectedby {@code this}
     */
    private boolean mayInterrupt;
    /**
     * @protectedby {@code this}
     */
    private boolean cancel;
    /**
     * @protectedby {@code this}
     */
    private Set<Thread> tasks;
    /**
     * @protectedby {@code this}
     */
    private List<RequestCancelHandler<O>> cancelHandlers;

    public CoreInboundRequest(final RequestListener<I, O> requestListener, final Executor executor, final ClientContext clientContext) {
        this.requestListener = requestListener;
        this.executor = executor;
        this.clientContext = clientContext;
    }

    private enum State implements org.jboss.cx.remoting.util.State<State> {
        INITIAL,
        UNSENT,
        SENT,
        TERMINATED;

        public boolean isReachable(final State dest) {
            return compareTo(dest) < 0;
        }
    }

    public void initialize(final RequestInitiator<O> requestInitiator) {
        state.requireTransitionExclusive(State.INITIAL, State.UNSENT);
        this.requestInitiator = requestInitiator;
        state.releaseExclusive();
    }

    public RequestResponder<I> getRequestResponder() {
        return request;
    }

    /**
     * Execute the given command.  The command will be sensitive to interruption if the request is cancelled.
     *
     * @param command the command to execute
     */
    private void executeTagged(final Runnable command) {
        executor.execute(new Runnable() {
            public void run() {
                final Thread thread = Thread.currentThread();
                synchronized(CoreInboundRequest.this) {
                    if (tasks == null) {
                        tasks = new HashSet<Thread>();
                    }
                    tasks.add(thread);
                }
                try {
                    command.run();
                } finally {
                    synchronized(CoreInboundRequest.this) {
                        tasks.remove(thread);
                    }
                }
            }
        });
    }

    public final class Request implements RequestResponder<I> {
        public void handleCancelRequest(final boolean mayInterrupt) {
            synchronized(CoreInboundRequest.this) {
                if (! cancel) {
                    cancel = true;
                    CoreInboundRequest.this.mayInterrupt |= mayInterrupt;
                    if (mayInterrupt) {
                        if (tasks != null) {
                            for (Thread t : tasks) {
                                t.interrupt();
                            }
                        }
                    }
                    if (cancelHandlers != null) {
                        final Iterator<RequestCancelHandler<O>> i = cancelHandlers.iterator();
                        while (i.hasNext()) {
                            final RequestCancelHandler<O> handler = i.next();
                            i.remove();
                            executor.execute(new Runnable() {
                                public void run() {
                                    handler.notifyCancel(userRequestContext, mayInterrupt);
                                }
                            });
                        }
                    }
                }
            }
        }

        public void handleRequest(final I request, final Executor streamExecutor) {
            executeTagged(new Runnable() {
                public void run() {
                    try {
                        requestListener.handleRequest(userRequestContext, request);
                    } catch (InterruptedException e) {
                        final boolean wasCancelled;
                        synchronized(CoreInboundRequest.this) {
                            wasCancelled = cancel;
                        }
                        if (state.transition(State.UNSENT, State.SENT)) {
                            if (wasCancelled) {
                                try {
                                    requestInitiator.handleCancelAcknowledge();
                                } catch (RemotingException e1) {
                                    try {
                                        requestInitiator.handleException(new RemoteExecutionException("Failed to send a cancel ack to client: " + e1.toString(), e1));
                                    } catch (RemotingException e2) {
                                        log.debug("Tried and failed to send an exception (%s): %s", e1, e2);
                                    }
                                }
                            } else {
                                try {
                                    requestInitiator.handleException(new RemoteExecutionException("Execution failed: " + e.toString(), e));
                                } catch (RemotingException e1) {
                                    log.debug("Tried and failed to send an exception (%s): %s", e, e1);
                                }
                            }
                            log.trace(e, "Request listener %s recevied an exception", requestListener);
                        }
                    } catch (Throwable e) {
                        if (state.transition(State.UNSENT, State.SENT)) {
                            try {
                                if (e instanceof RemoteExecutionException) {
                                    requestInitiator.handleException((RemoteExecutionException) e);
                                } else {
                                    requestInitiator.handleException(new RemoteExecutionException("Execution failed: " + e.toString(), e));
                                }
                            } catch (RemotingException e1) {
                                log.debug("Tried and failed to send an exception (%s): %s", e, e1);
                            }
                        }
                        log.trace(e, "Request listener %s recevied an exception", requestListener);
                    }
                }
            });
        }
    }

    public final class UserRequestContext implements RequestContext<O> {
        private UserRequestContext() {}

        public ClientContext getContext() {
            return clientContext;
        }

        public boolean isCancelled() {
            synchronized(CoreInboundRequest.this) {
                return cancel;
            }
        }

        public void sendReply(final O reply) throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            requestInitiator.handleReply(reply);
        }

        public void sendFailure(final String msg, final Throwable cause) throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            final RemoteExecutionException rex = new RemoteExecutionException(msg, cause);
            rex.setStackTrace(cause.getStackTrace());
            requestInitiator.handleException(rex);
        }

        public void sendCancelled() throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            requestInitiator.handleCancelAcknowledge();
        }

        public void addCancelHandler(final RequestCancelHandler<O> requestCancelHandler) {
            final boolean mayInterrupt;
            synchronized(CoreInboundRequest.this) {
                if (!cancel) {
                    if (cancelHandlers == null) {
                        cancelHandlers = new LinkedList<RequestCancelHandler<O>>();
                    }
                    cancelHandlers.add(requestCancelHandler);
                    return;
                }
                // otherwise, unlock and notify now
                mayInterrupt = CoreInboundRequest.this.mayInterrupt;
            }
            requestCancelHandler.notifyCancel(this, mayInterrupt);
        }

        public void execute(final Runnable command) {
            executeTagged(command);
        }
    }
}

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.log.Logger;
import java.util.concurrent.Executor;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;

/**
 *
 */
public final class CoreInboundRequest<I, O> {
    private static final Logger log = Logger.getLogger(CoreInboundRequest.class);

    private final RequestListener<I,O> requestListener;
    private final Executor executor;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final UserRequestContext userRequestContext = new UserRequestContext();
    private final RequestServer<I> request = new Request();

    private RequestClient<O> requestClient;

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

    public CoreInboundRequest(final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestListener = requestListener;
        this.executor = executor;
    }

    private enum State {
        INITIAL,
        UNSENT,
        SENT,
        TERMINATED,
    }

    public void initialize(final RequestClient<O> requestClient) {
        state.requireTransitionExclusive(State.INITIAL, State.UNSENT);
        this.requestClient = requestClient;
        state.releaseExclusive();
    }

    public RequestServer<I> getRequester() {
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

    public final class Request implements RequestServer<I> {
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
            state.requireTransition(State.INITIAL, State.UNSENT);
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
                                    requestClient.handleCancelAcknowledge();
                                } catch (RemotingException e1) {
                                    try {
                                        requestClient.handleException(new RemoteExecutionException("Failed to send a cancel ack to client: " + e1.toString(), e1));
                                    } catch (RemotingException e2) {
                                        log.debug("Tried and failed to send an exception (%s): %s", e1, e2);
                                    }
                                }
                            } else {
                                try {
                                    requestClient.handleException(new RemoteExecutionException("Execution failed: " + e.toString(), e));
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
                                    requestClient.handleException((RemoteExecutionException) e);
                                } else {
                                    requestClient.handleException(new RemoteExecutionException("Execution failed: " + e.toString(), e));
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

        public boolean isCancelled() {
            synchronized(CoreInboundRequest.this) {
                return cancel;
            }
        }

        public void sendReply(final O reply) throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            requestClient.handleReply(reply);
        }

        public void sendFailure(final String msg, final Throwable cause) throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            final RemoteExecutionException rex = new RemoteExecutionException(msg, cause);
            rex.setStackTrace(cause.getStackTrace());
            requestClient.handleException(rex);
        }

        public void sendCancelled() throws RemotingException, IllegalStateException {
            state.requireTransition(State.UNSENT, State.SENT);
            requestClient.handleCancelAcknowledge();
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

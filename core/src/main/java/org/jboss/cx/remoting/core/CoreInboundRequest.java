package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestCancelHandler;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
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

    private final RequestIdentifier requestIdentifier;
    private final CoreInboundContext<I, O> context;
    private final RequestListener<I,O> requestListener;
    private final Executor executor;

    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.INITIAL);
    private final UserRequestContext userRequestContext = new UserRequestContext();

    private boolean mayInterrupt;
    private boolean cancel;
    private Set<Thread> tasks;
    private List<RequestCancelHandler<O>> cancelHandlers;

    public CoreInboundRequest(final RequestIdentifier requestIdentifier, final CoreInboundContext<I, O> context, final RequestListener<I, O> requestListener, final Executor executor) {
        this.requestIdentifier = requestIdentifier;
        this.context = context;
        this.requestListener = requestListener;
        this.executor = executor;
    }

    private enum State {
        INITIAL,
        UNSENT,
        SENT,
    }

    void receiveRequest(final I request) {
        try {
            state.requireTransition(State.INITIAL, State.UNSENT);
            requestListener.handleRequest(userRequestContext, request);
        } catch (Throwable e) {
            if (state.transition(State.UNSENT, State.SENT)) {
                try {
                    if (e instanceof RemoteExecutionException) {
                        sendException((RemoteExecutionException) e);
                    } else {
                        sendException(new RemoteExecutionException("Execution failed: " + e.toString(), e));
                    }
                } catch (RemotingException e1) {
                    log.trace("Tried and failed to send an exception (%s) for a request (%s): %s", e, requestIdentifier, e1);
                }
            }
            log.trace(e, "Request listener %s recevied an exception for request (%s)", requestListener, requestIdentifier);
        }
    }

    void sendException(final RemoteExecutionException rex) throws RemotingException {
        context.sendException(requestIdentifier, rex);
    }

    public void receiveCancelRequest(final boolean mayInterrupt) {
        synchronized(this) {
            if (! cancel) {
                cancel = true;
                this.mayInterrupt = mayInterrupt;
                if (mayInterrupt) {
                    for (Thread t : tasks) {
                        t.interrupt();
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

    public final class UserRequestContext implements RequestContext<O> {

        public boolean isCancelled() {
            // todo...
            return false;
        }

        public void sendReply(final O reply) throws RemotingException, IllegalStateException {
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
    }
}

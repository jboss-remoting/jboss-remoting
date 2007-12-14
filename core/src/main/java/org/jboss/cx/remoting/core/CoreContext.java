package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Resource;
import org.jboss.cx.remoting.core.util.TypeMap;
import org.jboss.cx.remoting.core.util.LinkedHashTypeMap;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.ContextService;
import org.jboss.cx.remoting.spi.InterceptorContext;
import org.jboss.cx.remoting.spi.ClientInterceptor;
import org.jboss.cx.remoting.spi.AbstractClientInterceptor;

/**
 *
 */
public final class CoreContext<I, O> {

    private static final Logger log = Logger.getLogger(CoreContext.class);

    private final FirstInterceptor firstInterceptor = new FirstInterceptor();
    private final LastInterceptor lastInterceptor = new LastInterceptor();
    private final UserContext userContext = new UserContext();
    private final ContextIdentifier contextIdentifier;

    private final ProtocolHandler protocolHandler;
    private final ConcurrentMap<Object, Object> contextMap = CollectionUtil.concurrentMap();
    private final ConcurrentMap<RequestIdentifier, RemoteRequest> remoteRequests = CollectionUtil.concurrentMap();

    private final Resource resource = new Resource();
    private final TypeMap<ContextService> contextServices = new LinkedHashTypeMap<ContextService>();

    // Don't GC the session while a context lives
    private final CoreSession coreSession;

    public CoreContext(final CoreSession coreSession, final ContextIdentifier contextIdentifier) {
        this.coreSession = coreSession;
        this.contextIdentifier = contextIdentifier;
        protocolHandler = coreSession.getProtocolHandler();
        firstInterceptor.setNext(lastInterceptor);
        lastInterceptor.setPrevious(firstInterceptor);
        resource.doStart(null);
    }

    protected void shutdown() {
        resource.doStop(new Runnable() {
            public void run() {
                // todo - cancel all outstanding requests, send context close message via protocolHandler

            }
        }, null);
    }

    public CoreSession getSession() {
        return coreSession;
    }

    public ClientInterceptor getLastInterceptor() {
        return lastInterceptor;
    }

    public ClientInterceptor getFirstInterceptor() {
        return firstInterceptor;
    }

    public Context<I, O> getUserContext() {
        return userContext;
    }

    public Reply<O> createReply(Request<I> request, O body) {
        return new ReplyImpl<O>(body);
    }

    private void dropRequest(RequestIdentifier requestIdentifier) {
        remoteRequests.remove(requestIdentifier);
        resource.doRelease();
    }

    void handleInboundReply(final RequestIdentifier requestIdentifier, final Reply<O> reply) {
        RemoteRequest remoteRequest = remoteRequests.get(requestIdentifier);
        try {
            lastInterceptor.processInboundReply(new InboundReplyInterceptorContext(remoteRequest), requestIdentifier, reply);
        } catch (RuntimeException e) {
            // todo log failure
        }
    }

    void handleInboundException(final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        RemoteRequest remoteRequest = remoteRequests.get(requestIdentifier);
        try {
            lastInterceptor.processInboundException(new InboundReplyInterceptorContext(remoteRequest), requestIdentifier, exception);
        } catch (RuntimeException e) {
            // todo log failure
        }
    }

    public static class GenericInterceptorContext implements InterceptorContext {

    }

    public final class InboundReplyInterceptorContext extends GenericInterceptorContext {
        private final RemoteRequest remoteRequest;

        protected InboundReplyInterceptorContext(final RemoteRequest remoteRequest) {
            this.remoteRequest = remoteRequest;
        }
    }

    public final class FirstInterceptor extends AbstractClientInterceptor {
        private FirstInterceptor() {
            super(userContext);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply<?> reply) {
            if (log.isTrace()) {
                log.trace("Received inbound reply for request identifier " + requestIdentifier);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.remoteRequest.receiveReply((Reply<O>)reply);
        }

        public void processInboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
            if (log.isTrace()) {
                log.trace("Received inbound exception for request identifier " + requestIdentifier, exception);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.remoteRequest.setException(exception);
        }

        public void processInboundCancelAcknowledge(final InterceptorContext context, final RequestIdentifier requestIdentifier) {
            if (log.isTrace()) {
                log.trace("Received inbound cancel request for request identifier " + requestIdentifier);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.remoteRequest.setCancelled();
        }
    }

    public final class LastInterceptor extends AbstractClientInterceptor {
        private LastInterceptor() {
            super(userContext);
        }

        public void processOutboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, Request request) {
            if (log.isTrace()) {
                log.trace("Sending outbound request for request identifier " + requestIdentifier);
            }
            try {
                protocolHandler.sendRequest(contextIdentifier, requestIdentifier, request);
            } catch (Exception e) {
                log.trace("Outbound request transmission failed; sending inbound exception", e);
                processInboundException(context, requestIdentifier, new RemoteExecutionException("Failed to send request", e));
            }
        }

        public void processOutboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, Reply reply) {
            if (log.isTrace()) {
                log.trace("Sending outbound reply for request identifier " + requestIdentifier);
            }
            try {
                protocolHandler.sendReply(contextIdentifier, requestIdentifier, reply);
            } catch (Exception e) {
                // todo - should an outbound exception be sent?  maybe add retry logic?
                log.trace("Outbound reply transmission failed", e);
            }
        }

        public void processOutboundException(final InterceptorContext context, RequestIdentifier requestIdentifier, RemoteExecutionException exception) {
            if (log.isTrace()) {
                log.trace("Sending outbound exception for request identifier " + requestIdentifier, exception);
            }
            try {
                protocolHandler.sendException(contextIdentifier, requestIdentifier, exception);
            } catch (Exception e) {
                log.trace("Outbound exception transmission failed", e);
            }
        }
    }

    public final class UserContext implements Context<I, O> {

        private UserContext() {
        }

        public void close() throws RemotingException {
            // todo
        }

        public Request<I> createRequest(final I body) {
            return new RequestImpl<I>(body);
        }

        public Reply<O> invoke(Request<I> request) throws RemotingException, RemoteExecutionException, InterruptedException {
            // todo - do this better - ?
            return send(request).get();
        }

        public FutureReply<O> send(Request<I> request) throws RemotingException {
            // todo - make sure CoreFutureReply does a release on the context when it's done
            boolean ok = false;
            // todo - evaluate failure path to make sure nothing leaks
            final RequestIdentifier identifier;
            try {
                identifier = protocolHandler.openRequest(contextIdentifier);
            } catch (IOException e) {
                throw new RemotingException("Failed to open a request", e);
            }
            final RemoteRequest remoteRequest = new RemoteRequest(identifier);
            remoteRequests.put(identifier, remoteRequest);
            resource.doAcquire();
            try {
                final RemoteRequest.FutureReplyImpl futureReply = remoteRequest.new FutureReplyImpl();
                protocolHandler.sendRequest(contextIdentifier, identifier, request);
                ok = true;
                return futureReply;
            } catch (IOException e) {
                final RemotingException rex = new RemotingException("Failed to send request: " + e.getMessage());
                rex.setStackTrace(e.getStackTrace());
                throw rex;
            } finally {
                if (! ok) {
                    dropRequest(identifier);
                }
            }
        }

        public ConcurrentMap<Object, Object> getAttributes() {
            return contextMap;
        }

        public <T extends ContextService> T getService(Class<T> serviceType) throws RemotingException {
            final T service = contextServices.get(serviceType);
            if (service == null) {
                throw new RemotingException("Service type " + serviceType.getName() + " not supported");
            }
            return service;
        }

        public <T extends ContextService> boolean hasService(Class<T> serviceType) {
            return contextServices.containsKey(serviceType);
        }
    }

    private enum RemoteRequestState {
        WAITING,
        DONE,
        EXCEPTION,
        CANCELLED,
    }

    private final class RemoteRequest {
        private final AtomicStateMachine<RemoteRequestState> state = AtomicStateMachine.start(RemoteRequestState.WAITING);
        private final RequestIdentifier requestIdentifier;
        private final FutureReply<O> futureReply = new FutureReplyImpl();

        /* Protected by {@code state} */
        private Reply<O> reply;
        /* Protected by {@code state} */
        private RemoteExecutionException exception;
        /* Protected by {@code state} */
        private RequestCompletionHandler<O> handler;

        private RemoteRequest(final RequestIdentifier requestIdentifier) {
            this.requestIdentifier = requestIdentifier;
        }

        public void receiveReply(final Reply<O> reply) {
            synchronized(state) {
                if (state.transition(RemoteRequestState.WAITING, RemoteRequestState.DONE)) {
                    this.reply = reply;
                } else {
                    throw new IllegalStateException("Got reply from state " + state.getState());
                }
            }
        }

        public RemoteRequestState await() throws InterruptedException {
            return state.waitForNot(RemoteRequestState.WAITING);
        }

        public RemoteExecutionException getException() {
            synchronized(state) {
                state.require(RemoteRequestState.EXCEPTION);
                return exception;
            }
        }

        public Reply<O> getReply() {
            synchronized(state) {
                state.require(RemoteRequestState.DONE);
                return reply;
            }
        }

        public void setException(final RemoteExecutionException exception) {
            synchronized(state) {
                if (state.transition(RemoteRequestState.WAITING, RemoteRequestState.EXCEPTION)) {
                    this.exception = exception;
                }
                RequestCompletionHandler<O> handler = this.handler;
                this.handler = null;
                handler.notifyComplete(futureReply);
            }
        }

        public void sendCancel(boolean mayInterruptIfRunning) {
            lastInterceptor.processOutboundCancelRequest(null, requestIdentifier, mayInterruptIfRunning);
        }

        public void setCancelled() {
            synchronized(state) {
                if (state.transition(RemoteRequestState.WAITING, RemoteRequestState.CANCELLED)) {
                    RequestCompletionHandler<O> handler = this.handler;
                    this.handler = null;
                    handler.notifyComplete(futureReply);
                }
            }
        }

        public final class FutureReplyImpl implements FutureReply<O> {

            private FutureReplyImpl() {
            }

            protected void finalize() throws Throwable {
                super.finalize();
                // the user no longer cares about the request, so remove it from the map
                remoteRequests.remove(requestIdentifier);
            }

            public boolean cancel(final boolean mayInterruptIfRunning) {
                state.doIf(new Runnable() {
                    public void run() {
                        sendCancel(mayInterruptIfRunning);
                    }
                }, RemoteRequestState.WAITING);
                return state.waitUninterruptiblyForNot(RemoteRequestState.WAITING) == RemoteRequestState.CANCELLED;
            }

            public boolean isCancelled() {
                return state.in(RemoteRequestState.CANCELLED);
            }

            public boolean isDone() {
                return state.in(RemoteRequestState.DONE);
            }

            public Reply<O> get() throws InterruptedException, CancellationException, RemoteExecutionException {
                switch (state.waitForNot(RemoteRequestState.WAITING)) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                }
                throw new IllegalStateException("Wrong state");
            }

            public Reply<O> get(long timeout, TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException, TimeoutException {
                switch (state.waitForNot(RemoteRequestState.WAITING, timeout, unit)) {
                    case CANCELLED:
                        throw new CancellationException("Request was cancelled");
                    case EXCEPTION:
                        throw exception;
                    case DONE:
                        return reply;
                    case WAITING:
                        throw new TimeoutException("Timed out while waiting for reply");
                }
                throw new IllegalStateException("Wrong state");
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
                            RemoteRequest.this.handler = handler;
                            break;
                    }
                }
                return this;
            }
        }
    }

}

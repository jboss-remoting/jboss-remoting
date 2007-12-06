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
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.FutureReply;
import org.jboss.cx.remoting.RequestCompletionHandler;
import org.jboss.cx.remoting.RequestListener;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Resource;
import org.jboss.cx.remoting.core.util.TypeMap;
import org.jboss.cx.remoting.core.util.LinkedHashTypeMap;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.ContextService;
import org.jboss.cx.remoting.spi.AbstractContextInterceptor;
import org.jboss.cx.remoting.spi.ContextInterceptor;

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
    private final ConcurrentMap<RequestIdentifier, ExecutingRequest> remoteRequests = CollectionUtil.concurrentMap();

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

    public void close() throws RemotingException {
        resource.doStop(new Runnable() {
            public void run() {
                // todo - cancel all outstanding requests, send context close message via protocolHandler
            }
        }, null);
    }

    public CoreSession getSession() {
        return coreSession;
    }

    public ContextInterceptor getLastInterceptor() {
        return lastInterceptor;
    }

    public ContextInterceptor getFirstInterceptor() {
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

    void handleInboundRequest(final RequestIdentifier requestIdentifier, final Request<I> request) {
        try {
            lastInterceptor.processInboundRequest(new GenericInterceptorContext(), requestIdentifier, request);
        } catch (RuntimeException e) {
            // todo log failure
        }
    }

    void handleInboundReply(final RequestIdentifier requestIdentifier, final Reply<O> reply) {
        ExecutingRequest executingRequest = remoteRequests.get(requestIdentifier);
        try {
            lastInterceptor.processInboundReply(new InboundReplyInterceptorContext(executingRequest), requestIdentifier, reply);
        } catch (RuntimeException e) {
            // todo log failure
        }
    }

    void handleInboundException(final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        ExecutingRequest executingRequest = remoteRequests.get(requestIdentifier);
        try {
            lastInterceptor.processInboundException(new InboundReplyInterceptorContext(executingRequest), requestIdentifier, exception);
        } catch (RuntimeException e) {
            // todo log failure
        }
    }

    public class GenericInterceptorContext implements ContextInterceptor.InterceptorContext {

    }

    public final class InboundReplyInterceptorContext extends GenericInterceptorContext {
        private final ExecutingRequest executingRequest;

        protected InboundReplyInterceptorContext(final ExecutingRequest executingRequest) {
            this.executingRequest = executingRequest;
        }
    }

    public final class FirstInterceptor extends AbstractContextInterceptor {
        private FirstInterceptor() {
            super(userContext);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply<?> reply) {
            if (log.isTrace()) {
                log.trace("Received inbound reply for request identifier " + requestIdentifier);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.executingRequest.complete((Reply<O>)reply);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
            if (log.isTrace()) {
                log.trace("Received inbound exception for request identifier " + requestIdentifier, exception);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.executingRequest.setException(exception);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundCancelRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final boolean mayInterruptIfRunning) {
            if (log.isTrace()) {
                log.trace("Received inbound cancel request for request identifier " + requestIdentifier);
            }
            InboundReplyInterceptorContext iContext = (InboundReplyInterceptorContext) context;
            iContext.executingRequest.cancel(mayInterruptIfRunning);
        }

        @SuppressWarnings ({"unchecked"})
        public void processInboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Request<?> request) {
            if (log.isTrace()) {
                log.trace("Received inbound request for request identifier " + requestIdentifier);
            }
            // todo
            final RequestListener<I, O> requestListener = null;
            final boolean trace = log.isTrace();
            if (requestListener == null) {
                processOutboundException(context, requestIdentifier, new RemoteExecutionException("No such operation on this service"));
                return;
            }
            final ReceivedRequest receivedRequest = new ReceivedRequest(requestIdentifier);
            try {
                requestListener.handleRequest(receivedRequest.requestContext, (Request<I>) request);
            } catch (RemoteExecutionException e) {
                processOutboundException(context, requestIdentifier, e);
            } catch (RuntimeException e) {
                processOutboundException(context, requestIdentifier, new RemoteExecutionException("Request execution failed", e));
            } catch (InterruptedException e) {
                // todo
                e.printStackTrace();
            }
        }

    }

    public final class LastInterceptor extends AbstractContextInterceptor {
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
            final ExecutingRequest executingRequest = new ExecutingRequest(request, identifier);
            remoteRequests.put(identifier, executingRequest);
            resource.doAcquire();
            try {
                final ExecutingRequest.FutureReplyImpl futureReply = executingRequest.new FutureReplyImpl();
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

    public final class ReceivedRequest {
        private final RequestContext<O> requestContext = new CoreRequestContext();

        private final RequestIdentifier requestIdentifier;

        private volatile boolean cancelled = false;

        private ReceivedRequest(final RequestIdentifier requestIdentifier) {
            this.requestIdentifier = requestIdentifier;
        }

        public final class CoreRequestContext implements RequestContext<O> {

            private CoreRequestContext() {}

            public boolean isCancelled() {
                return cancelled;
            }

            public Reply<O> createReply(O body) {
                return new ReplyImpl<O>(body);
            }

            public void sendReply(Reply<O> reply) throws RemotingException, IllegalStateException {
                // todo
                firstInterceptor.processOutboundReply(null, requestIdentifier, reply);
            }

            public void sendFailure(String msg, Throwable cause) throws RemotingException, IllegalStateException {
                // todo
                firstInterceptor.processOutboundException(null, requestIdentifier, new RemoteExecutionException(msg, cause));
            }

            public void sendCancelled() throws RemotingException, IllegalStateException {
                firstInterceptor.processOutboundCancelAcknowledge(null, requestIdentifier);
            }

        }

    }

    private enum ExecutingRequestState {
        WAITING,
        DONE,
        EXCEPTION,
        CANCELLED,
    }

    private final class ExecutingRequest {
        private Reply<O> reply;
        private RemoteExecutionException exception;
        private ExecutingRequestState state = ExecutingRequestState.WAITING;
        private final Object monitor = new Object();
        private final Request<I> request;
        private final RequestIdentifier requestIdentifier;

        private ExecutingRequest(final Request<I> request, final RequestIdentifier requestIdentifier) {
            this.request = request;
            this.requestIdentifier = requestIdentifier;
        }

        public boolean complete(final Reply<O> reply) {
            synchronized(monitor) {
                if (state != ExecutingRequestState.WAITING) {
                    return false;
                }
                this.reply = reply;
                state = ExecutingRequestState.DONE;
                monitor.notifyAll();
                return true;
            }
        }

        public ExecutingRequestState await() throws InterruptedException {
            synchronized(monitor) {
                for(;;) {
                    if (state != ExecutingRequestState.WAITING) {
                        return state;
                    }
                    monitor.wait();
                }
            }
        }

        public RemoteExecutionException getException() {
            synchronized(monitor) {
                if (state != ExecutingRequestState.EXCEPTION) {
                    throw new IllegalStateException("No exception available");
                }
                return exception;
            }
        }

        public Reply<O> getReply() {
            return reply;
        }

        public void setException(final RemoteExecutionException exception) {
            synchronized(monitor) {
                if (state != ExecutingRequestState.WAITING) {
                    // ignore...
                    return;
                }
                this.exception = exception;
                state = ExecutingRequestState.EXCEPTION;
                monitor.notifyAll();
            }
        }

        public void sendCancel(boolean mayInterruptIfRunning) {
            
        }

        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized(monitor) {
                if (state == ExecutingRequestState.WAITING) {
                    lastInterceptor.processOutboundCancelRequest(null/*todo*/, requestIdentifier, true);
                    state = ExecutingRequestState.CANCELLED;
                    // todo notify
                    return true;
                }
                return state == ExecutingRequestState.CANCELLED;
            }
        }

        public final class FutureReplyImpl implements FutureReply<O> {

            private RequestCompletionHandler handler;

            private FutureReplyImpl() {
            }

            protected void finalize() throws Throwable {
                super.finalize();
                // the user no longer cares about the request, so remove it from the map
                remoteRequests.remove(requestIdentifier);
            }

            public boolean cancel(boolean mayInterruptIfRunning) {
                return ExecutingRequest.this.cancel(mayInterruptIfRunning);
            }

            public boolean isCancelled() {
                synchronized(monitor) {
                    return state == ExecutingRequestState.CANCELLED;
                }
            }

            public boolean isDone() {
                synchronized(monitor) {
                    return state == ExecutingRequestState.DONE;
                }
            }

            public Reply<O> get() throws InterruptedException, CancellationException, RemoteExecutionException {
                synchronized(monitor) {
                    while (state == ExecutingRequestState.WAITING) {
                        monitor.wait();
                    }
                    switch (state) {
                        case CANCELLED:
                            throw new CancellationException("Request was cancelled");
                        case EXCEPTION:
                            throw exception;
                        case DONE:
                            return reply;
                    }
                }
                throw new IllegalStateException("Wrong state");
            }

            public Reply<O> get(long timeout, TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException, TimeoutException {
                synchronized(monitor) {
                    while (state == ExecutingRequestState.WAITING) {
                        unit.timedWait(monitor, timeout);
                    }
                    switch (state) {
                        case CANCELLED:
                            throw new CancellationException("Request was cancelled");
                        case EXCEPTION:
                            throw exception;
                        case DONE:
                            return reply;
                    }
                }
                throw new IllegalStateException("Wrong state");
            }

            public FutureReply<O> setCompletionNotifier(RequestCompletionHandler handler) {
                synchronized(monitor) {
                    switch (state) {
                        case CANCELLED:
                        case DONE:
                        case EXCEPTION:
                            handler.notifyComplete(this);
                            break;
                        case WAITING:
                            this.handler = handler;
                    }
                }
                return this;
            }

        }
    }

}

package org.jboss.cx.remoting.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.FutureTask;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Reply;
import org.jboss.cx.remoting.Request;
import org.jboss.cx.remoting.core.util.CollectionUtil;
import org.jboss.cx.remoting.core.util.Logger;
import org.jboss.cx.remoting.spi.AbstractContextInterceptor;
import org.jboss.cx.remoting.spi.protocol.RequestIdentifier;

/**
 *
 */
public final class ThreadPoolContextInterceptor extends AbstractContextInterceptor {
    private static final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(30, 50, 0, null, new ArrayBlockingQueue<Runnable>(100, true));
    private final ConcurrentMap<RequestIdentifier, Future<Void>> requests = CollectionUtil.concurrentWeakHashMap();

    private static final Logger log = Logger.getLogger(ThreadPoolContextInterceptor.class);

    public ThreadPoolContextInterceptor(final Context<?, ?> context) {
        super(context);
    }

    public void processInboundCancelRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final boolean mayInterruptIfRunning) {
        try {
            super.processInboundCancelRequest(context, requestIdentifier, false);
        } finally {
            final Future<Void> future = requests.get(requestIdentifier);
            if (future != null) {
                if (future.cancel(mayInterruptIfRunning)) {

                }
            }
        }
    }

    public void processInboundRequest(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Request request) {
        try {
            // Use FutureTask so that we get a Future<> before the task actually starts
            FutureTask<Void> task = new FutureTask<Void>(new Runnable() {
                public void run() {
                    ThreadPoolContextInterceptor.super.processInboundRequest(context, requestIdentifier, request);
                }
            }, null);
            requests.put(requestIdentifier, task);
            threadPoolExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            processOutboundException(context, requestIdentifier, new RemoteExecutionException("Request job submission rejected (the server may be too busy to service this request)", e));
        }
    }

    public void processOutboundCancelAcknowledge(final InterceptorContext context, final RequestIdentifier requestIdentifier) {
        requests.remove(requestIdentifier);
        super.processOutboundCancelAcknowledge(context, requestIdentifier);
    }

    public void processOutboundReply(final InterceptorContext context, final RequestIdentifier requestIdentifier, final Reply reply) {
        requests.remove(requestIdentifier);
        super.processOutboundReply(context, requestIdentifier, reply);
    }

    public void processOutboundException(final InterceptorContext context, final RequestIdentifier requestIdentifier, final RemoteExecutionException exception) {
        requests.remove(requestIdentifier);
        super.processOutboundException(context, requestIdentifier, exception);
    }

}

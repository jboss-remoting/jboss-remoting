package org.jboss.cx.remoting;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The result of an invocation that may or may not yet have completed.
 * <p/>
 * In addition to representing the invocation results, this interface allows the user to cancel the request, or schedule
 * an asynchronous callback for when the request completes.
 */
public interface FutureReply<T> extends Future<T> {

    /**
     * Attempts to cancel execution of this request.  This attempt will fail if the request has already completed,
     * already been cancelled, or could not be cancelled for some other reason.  The {@code mayInterruptIfRunning}
     * parameter determines whether the thread executing this task should be interrupted in an attempt to stop the
     * task.  If {@code false}, the thread will not be interrupted.  If {@code true}, then the remote service's
     * interruption policy will be used.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this task should be interrupted; otherwise,
     * in-progress tasks are allowed to complete
     *
     * @return {@code false} if the task could not be cancelled, typically because it has already completed normally;
     *         {@code true} otherwise
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Asynchronously send a request to cancel this request.  Does not block the current method.  Use the
     * {@link #addCompletionNotifier(RequestCompletionHandler)} method to add a notifier to be called upon completion.
     *
     * @param mayInterruptIfRunning
     */
    FutureReply<T> sendCancel(boolean mayInterruptIfRunning);

    /**
     * Returns {@code true} if this task was cancelled before it completed normally.
     *
     * @return {@code true} if task was cancelled before it completed
     */
    boolean isCancelled();

    /**
     * Returns {@code true} if this request completed.
     * <p/>
     * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method
     * will return {@code true}.
     *
     * @return {@code true} if this request completed
     */
    boolean isDone();

    /**
     * Waits if necessary for the request to complete, and then retrieves its reply.
     *
     * @return the reply
     *
     * @throws CancellationException if the computation was cancelled
     * @throws RemoteExecutionException if the computation threw an exception
     */
    T get() throws CancellationException, RemoteExecutionException;

    /**
     * Waits if necessary for the request to complete, and then retrieves its reply.
     *
     * @return the reply
     *
     * @throws CancellationException if the computation was cancelled
     * @throws RemoteExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    T getInterruptibly() throws InterruptedException, CancellationException, RemoteExecutionException;

    /**
     * Waits if necessary for at most the given time for the request to complete, and then retrieves the reply, if
     * available.  If no reply was available, {@code null} is returned.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     *
     * @return the reply, or {@code null} if the operation timed out
     *
     * @throws CancellationException if the computation was cancelled
     * @throws RemoteExecutionException if the computation threw an exception
     */
    T get(long timeout, TimeUnit unit) throws CancellationException, RemoteExecutionException;

    /**
     * Waits if necessary for at most the given time for the request to complete, and then retrieves the reply, if
     * available.  If no reply was available, {@code null} is returned.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     *
     * @return the reply, or {@code null} if the operation timed out
     *
     * @throws CancellationException if the computation was cancelled
     * @throws RemoteExecutionException if the computation threw an exception
     * @throws InterruptedException if the current thread was interrupted while waiting
     */
    T getInterruptibly(long timeout, TimeUnit unit) throws InterruptedException, CancellationException, RemoteExecutionException;

    /**
     * Add a notifier to be called when the request has completed.  The notifier may be called from the current thread
     * or a different thread.  If the request has already completed, the notifier will be called immediately. Calling
     * this method guarantees that the supplied handler will be called.  The handler may be called at any time after
     * the request has completed, though implementations should make a reasonable effort to ensure that the handler is
     * called in a timely manner.
     * <p/>
     * This method returns {@code this} in order to facilitate method call chaining.
     *
     * @param handler the handler to add, or {@code null} to clear the handler
     *
     * @return this future reply
     */
    FutureReply<T> addCompletionNotifier(RequestCompletionHandler<T> handler);
}

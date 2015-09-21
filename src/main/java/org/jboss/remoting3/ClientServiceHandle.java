/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import static org.xnio.IoUtils.safeClose;

import java.io.IOException;
import java.util.function.Function;

import org.xnio.FinishedIoFuture;
import org.xnio.FutureResult;
import org.xnio.IoFuture;
import org.xnio.OptionMap;

/**
 * A handle for helping service protocol providers to create and maintain a single channel per connection.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ClientServiceHandle<T> {
    private final Attachments.Key<IoFuture<T>> key;
    private final String serviceName;
    private final Function<Channel, IoFuture<T>> constructor;

    /**
     * Construct a new instance.  Only one instance should be constructed per service; instances may be safely
     * cached in {@code static} fields.
     *
     * @param serviceName the service name (may not be {@code null})
     * @param constructor the service future construction operation
     */
    @SuppressWarnings("unchecked")
    public ClientServiceHandle(final String serviceName, final Function<Channel, IoFuture<T>> constructor) {
        key = (Attachments.Key<IoFuture<T>>) (Object) new Attachments.Key<>(IoFuture.class);
        this.serviceName = serviceName;
        this.constructor = constructor;
    }

    /**
     * Get or establish the future client service for the given connection.
     *
     * @param connection the connection
     * @param optionMap the service options
     * @return the future service instance
     */
    public IoFuture<T> getClientService(final Connection connection, OptionMap optionMap) {
        final Attachments attachments = connection.getAttachments();

        // check for prior
        IoFuture<T> existing = attachments.getAttachment(key);
        if (existing != null) {
            return existing;
        }

        // create new future result, try to attach it
        final FutureResult<T> futureResult = new FutureResult<>(connection.getEndpoint().getXnioWorker());
        final IoFuture<T> future = futureResult.getIoFuture();
        existing = attachments.attachIfAbsent(key, future);
        if (existing != null) {
            return existing;
        }

        // open the channel and create notifiers to trigger user construction operation
        final IoFuture<Channel> futureChannel = connection.openChannel(serviceName, optionMap);
        futureChannel.addNotifier(new IoFuture.HandlingNotifier<Channel, FutureResult<T>>() {
            public void handleCancelled(final FutureResult<T> futureResult) {
                futureResult.setCancelled();
                attachments.removeAttachment(key, futureResult.getIoFuture());
            }

            public void handleFailed(final IOException exception, final FutureResult<T> futureResult) {
                futureResult.setException(exception);
                attachments.removeAttachment(key, futureResult.getIoFuture());
            }

            public void handleDone(final Channel channel, final FutureResult<T> futureResult) {
                final IoFuture<T> nextFuture = constructor.apply(channel);
                nextFuture.addNotifier(new IoFuture.HandlingNotifier<T, FutureResult<T>>() {
                    public void handleCancelled(final FutureResult<T> futureResult) {
                        safeClose(channel);
                        futureResult.setCancelled();
                        attachments.removeAttachment(key, futureResult.getIoFuture());
                    }

                    public void handleFailed(final IOException exception, final FutureResult<T> futureResult) {
                        safeClose(channel);
                        futureResult.setException(exception);
                        attachments.removeAttachment(key, futureResult.getIoFuture());
                    }

                    public void handleDone(final T result, final FutureResult<T> futureResult) {
                        // Publish result
                        futureResult.setResult(result);
                        // Optimize overall
                        attachments.replaceAttachment(key, futureResult.getIoFuture(), new FinishedIoFuture<T>(result));
                    }
                }, futureResult);
                // make sure cancel requests now pass up to the service future
                futureResult.addCancelHandler(nextFuture);
            }
        }, futureResult);
        // make sure cancel requests pass up to the channel open request
        futureResult.addCancelHandler(futureChannel);
        return future;
    }
}

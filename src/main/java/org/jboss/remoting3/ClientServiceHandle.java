/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        final FutureResult<T> futureResult = new FutureResult<>();
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
                        // Remove on close
                        channel.addCloseHandler((closed, exception) -> attachments.removeAttachment(key));
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

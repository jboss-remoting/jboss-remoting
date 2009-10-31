/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.ServiceLoader;
import java.util.Map;
import java.util.HashMap;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RemotingServiceDescriptor;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.ProtocolServiceType;
import org.jboss.xnio.CloseableExecutor;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.log.Logger;
import org.jboss.marshalling.ClassTable;
import org.jboss.marshalling.ObjectTable;
import org.jboss.marshalling.ClassResolver;
import org.jboss.marshalling.ObjectResolver;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.ProviderDescriptor;
import org.jboss.marshalling.MarshallerFactory;

/**
 * The standalone interface into Remoting.  This class contains static methods that are useful to standalone programs
 * for managing endpoints and services in a simple fashion.
 *
 * @apiviz.landmark
 */
public final class Remoting {

    private static final Logger log = Logger.getLogger("org.jboss.remoting");

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of 10 threads.
     * <p>
     * You must have the {@link org.jboss.remoting3.EndpointPermission createEndpoint EndpointPermission} to invoke this method.
     *
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name) throws IOException {
        return createEndpoint(name, 10);
    }

    /**
     * Create an endpoint.  The endpoint will create its own thread pool with a maximum of {@code maxThreads} threads.
     * <p>
     * You must have the {@link org.jboss.remoting3.EndpointPermission createEndpoint EndpointPermission} to invoke this method.
     *
     * @param name the name of the endpoint
     * @param maxThreads the maximum thread count
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final String name, final int maxThreads) throws IOException {
        return createEndpoint(name, OptionMap.builder().set(Options.MAX_THREADS, maxThreads).getMap());
    }

    /**
     * Create an endpoint configured with the given option map.  The following options are supported:
     * <ul>
     * <li>{@link Options#MAX_THREADS} - specify the maximum number of threads for the created thread pool (default 10)</li>
     * <li>{@link Options#LOAD_PROVIDERS} - specify whether providers should be auto-loaded (default {@code true})</li>
     * </ul>
     *
     * @param endpointName the endpoint name
     * @param optionMap the endpoint options
     * @return the endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final OptionMap optionMap) throws IOException {
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        final CloseableExecutor executor = createExecutor(optionMap.get(Options.MAX_THREADS, 10));
        final Endpoint endpoint = createEndpoint(executor, endpointName);
        endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
            public void handleClose(final Endpoint closed) {
                IoUtils.safeClose(executor);
            }
        });
        if (optionMap.get(Options.LOAD_PROVIDERS, true)) {
            for (RemotingServiceDescriptor<?> descriptor : ServiceLoader.load(RemotingServiceDescriptor.class)) {
                final String name = descriptor.getName();
                final Class<?> serviceType = descriptor.getType();
                final Object service = descriptor.getService();
                try {
                    if (serviceType == ConnectionProviderFactory.class) {
                        endpoint.addConnectionProvider(name, (ConnectionProviderFactory<?>) service);
                    } else if (serviceType == ClassTable.class) {
                        endpoint.addProtocolService(ProtocolServiceType.CLASS_TABLE, name, (ClassTable) service);
                    } else if (serviceType == ObjectTable.class) {
                        endpoint.addProtocolService(ProtocolServiceType.OBJECT_TABLE, name, (ObjectTable) service);
                    } else if (serviceType == ClassResolver.class) {
                        endpoint.addProtocolService(ProtocolServiceType.CLASS_RESOLVER, name, (ClassResolver) service);
                    } else if (serviceType == ObjectResolver.class) {
                        endpoint.addProtocolService(ProtocolServiceType.OBJECT_RESOLVER, name, (ObjectResolver) service);
                    } else if (serviceType == ClassExternalizerFactory.class) {
                        endpoint.addProtocolService(ProtocolServiceType.CLASS_EXTERNALIZER_FACTORY, name, (ClassExternalizerFactory) service);
                    }
                } catch (DuplicateRegistrationException e) {
                    log.debug("Duplicate registration for '" + name + "' of " + serviceType);
                }
            }
            final Map<String, ProviderDescriptor> found = new HashMap<String, ProviderDescriptor>();
            for (ProviderDescriptor descriptor : ServiceLoader.load(ProviderDescriptor.class)) {
                final String name = descriptor.getName();
                // find the best one
                if (! found.containsKey(name) || found.get(name).getSupportedVersions()[0] < descriptor.getSupportedVersions()[0]) {
                    found.put(name, descriptor);
                }
            }
            for (String name : found.keySet()) {
                final MarshallerFactory marshallerFactory = found.get(name).getMarshallerFactory();
                try {
                    endpoint.addProtocolService(ProtocolServiceType.MARSHALLER_FACTORY, name, marshallerFactory);
                } catch (DuplicateRegistrationException e) {
                    log.debug("Duplicate registration for '" + name + "' of " + MarshallerFactory.class);
                }
            }
        }
        return endpoint;
    }

    private static final ThreadFactory OUR_THREAD_FACTORY = new ThreadFactory() {
        private final ThreadFactory defaultThreadFactory = Executors.defaultThreadFactory();

        public Thread newThread(final Runnable r) {
            final Thread thread = defaultThreadFactory.newThread(r);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                public void uncaughtException(final Thread t, final Throwable e) {
                    log.error(e, "Uncaught exception in thread %s", t);
                }
            });
            return thread;
        }
    };

    /**
     * Create a simple thread pool that is compatible with Remoting.  The thread pool will have a maximum of {@code maxThreads}
     * threads.
     *
     * @param maxThreads the maximum thread count
     * @return a closeable executor
     */
    public static CloseableExecutor createExecutor(final int maxThreads) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, maxThreads, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(50), OUR_THREAD_FACTORY, new ThreadPoolExecutor.CallerRunsPolicy());
        return new CloseableExecutor() {
            public void close() throws IOException {
                executor.shutdown();
            }

            public void execute(final Runnable command) {
                executor.execute(command);
            }
        };
    }

    /**
     * Create an endpoint using the given {@code Executor} to execute tasks.
     *
     * @param executor the executor to use
     * @param name the name of the endpoint
     * @return the endpoint
     */
    public static Endpoint createEndpoint(final Executor executor, final String name) throws IOException {
        return new EndpointImpl(executor, name);
    }

    /**
     * Create a local client from a request listener.  The client will retain the sole reference to the request listener,
     * so when the client is closed, the listener will also be closed (unless the client is sent to a remote endpoint).
     *
     * @param endpoint the endpoint to bind the request listener to
     * @param requestListener the request listener
     * @param requestClass the request class
     * @param replyClass the reply class
     * @param <I> the request type
     * @param <O> the reply type
     * @return a new client
     * @throws IOException if an error occurs
     */
    public static <I, O> Client<I, O> createLocalClient(final Endpoint endpoint, final RequestListener<I, O> requestListener, final Class<I> requestClass, final Class<O> replyClass) throws IOException {
        boolean ok = false;
        final RequestHandler requestHandler = endpoint.createLocalRequestHandler(requestListener, requestClass, replyClass);
        try {
            final Client<I, O> client = endpoint.createClient(requestHandler, requestClass, replyClass);
            ok = true;
            return client;
        } finally {
            if (! ok) {
                IoUtils.safeClose(requestHandler);
            }
        }
    }

    private Remoting() { /* empty */ }
}

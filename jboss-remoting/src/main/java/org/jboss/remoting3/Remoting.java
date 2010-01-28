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
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.ServiceLoader;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.lang.reflect.InvocationTargetException;
import org.jboss.remoting3.spi.RequestHandler;
import org.jboss.remoting3.spi.RemotingServiceDescriptor;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.jboss.remoting3.spi.ProtocolServiceType;
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

    private static Endpoint configuredEndpoint;
    private static final Object lock = new Object();

    private static final EndpointPermission CREATE_ENDPOINT_PERM = new EndpointPermission("createEndpoint");
    private static final EndpointPermission GET_CONFIGURED_ENDPOINT_PERM = new EndpointPermission("getConfiguredEndpoint");

    private static final String PROPERTIES = "remoting.properties";
    private static final String PROPERTY_FILE_PROPNAME = "remoting.property.file";

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

    public static Endpoint getConfiguredEndpoint() throws IOException {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(GET_CONFIGURED_ENDPOINT_PERM);
        }
        synchronized (lock) {
            final Endpoint endpoint = configuredEndpoint;
            if (endpoint != null) {
                return endpoint;
            }
            return configuredEndpoint = createConfigured();
        }
    }

    private static Endpoint createConfigured() throws IOException {
        try {
            return AccessController.doPrivileged(new PrivilegedAction<Endpoint>() {
                public Endpoint run() {
                    boolean ok = false;
                    final String fileName = System.getProperty(PROPERTY_FILE_PROPNAME, PROPERTIES);
                    final Properties props = new Properties();
                    try {
                        final InputStream stream = getClass().getResourceAsStream(fileName);
                        if (stream != null) try {
                            final InputStreamReader reader = new InputStreamReader(stream, "utf-8");
                            try {
                                props.load(reader);
                                reader.close();
                            } finally {
                                IoUtils.safeClose(reader);
                            }
                        } finally {
                            IoUtils.safeClose(stream);
                        }
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                            Integer.parseInt(props.getProperty("endpoint.threadpool.coresize", "8")),
                            Integer.parseInt(props.getProperty("endpoint.threadpool.maxsize", "64")),
                            Long.parseLong(props.getProperty("endpoint.threadpool.keepaliveseconds", "30")),
                            TimeUnit.SECONDS,
                            new ArrayBlockingQueue<Runnable>(Integer.parseInt(props.getProperty("endpoint.threadpool.queuelength", "64"))),
                            OUR_THREAD_FACTORY,
                            new ThreadPoolExecutor.CallerRunsPolicy()
                    );
                    try {
                        final Endpoint endpoint;
                        try {
                            endpoint = createEndpoint(props.getProperty("endpoint.name", "endpoint"), executor, OptionMap.builder().parseAll(props, "endpoint.option.").getMap());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        try {
                            endpoint.addCloseHandler(new CloseHandler<Endpoint>() {
                                public void handleClose(final Endpoint closed) {
                                    executor.shutdown();
                                }
                            });
                            addServices(endpoint, ProtocolServiceType.CLASS_TABLE, props);
                            addServices(endpoint, ProtocolServiceType.OBJECT_TABLE, props);
                            addServices(endpoint, ProtocolServiceType.CLASS_RESOLVER, props);
                            addServices(endpoint, ProtocolServiceType.OBJECT_RESOLVER, props);
                            addServices(endpoint, ProtocolServiceType.CLASS_EXTERNALIZER_FACTORY, props);
                            for (RemotingServiceDescriptor<?> descriptor : ServiceLoader.load(RemotingServiceDescriptor.class)) {
                                final String name = descriptor.getName();
                                final Class<?> serviceType = descriptor.getType();
                                final Object service;
                                try {
                                    service = descriptor.getService(props);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
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
                                    log.warn("Duplicate registration for '" + name + "' of " + serviceType);
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
                                try {
                                    endpoint.addProtocolService(ProtocolServiceType.MARSHALLER_PROVIDER_DESCRIPTOR, name, found.get(name));
                                } catch (DuplicateRegistrationException e) {
                                    log.warn("Duplicate registration for '" + name + "' of " + MarshallerFactory.class);
                                }
                            }
                            ok = true;
                            return endpoint;
                        } finally {
                            if (! ok) {
                                IoUtils.safeClose(endpoint);
                            }
                        }
                    } finally {
                        if (!ok) {
                            executor.shutdown();
                            try {
                                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            });
        } catch (RuntimeException e) {
            final Throwable c = e.getCause();
            if (c instanceof IOException) {
                throw (IOException) c;
            }
            throw e;
        }
    }

    private static <T> void addServices(final Endpoint endpoint, final ProtocolServiceType<T> serviceType, final Properties props) {
        final String basePropName = serviceType.getName().toLowerCase();
        final String instances = props.getProperty(endpoint.getName() + "." + basePropName + "_list");
        final Class<T> valueClass = serviceType.getValueClass();
        if (instances != null) {
            for (String name : instances.split(",")) {
                final String trimmed = name.trim();
                final String className = props.getProperty(name + "." + basePropName + "." + trimmed + ".class");
                if (className != null) {
                    try {
                        final Class<? extends T> instanceType = Class.forName(className).asSubclass(valueClass);
                        final T instance = instanceType.getConstructor().newInstance();
                        endpoint.addProtocolService(serviceType, name, instance);
                    } catch (InvocationTargetException e) {
                        log.warn(e.getCause(), "Unable to create %s instance '%s'", serviceType, name);
                    } catch (Exception e) {
                        log.warn("Unable to register %s '%s': %s", serviceType, name, e);
                    }
                }
            }
        }
    }

    /**
     * Create an endpoint configured with the given option map.  The following options are supported:
     * <ul>
     * </ul>
     *
     * @param endpointName the endpoint name
     * @param executor the thread pool to use
     * @param optionMap the endpoint options
     * @return the endpoint
     * @throws IOException if an error occurs
     */
    public static Endpoint createEndpoint(final String endpointName, final Executor executor, final OptionMap optionMap) throws IOException {
        if (endpointName == null) {
            throw new NullPointerException("endpointName is null");
        }
        if (optionMap == null) {
            throw new NullPointerException("optionMap is null");
        }
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(CREATE_ENDPOINT_PERM);
        }
        final Endpoint endpoint = new EndpointImpl(executor, endpointName);
        return endpoint;
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

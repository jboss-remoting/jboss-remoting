/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongFunction;
import java.util.function.Predicate;

import javax.security.sasl.SaslClientFactory;

import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.wildfly.common.Assert;
import org.wildfly.common.context.ContextManager;
import org.wildfly.common.function.ExceptionBiConsumer;
import org.wildfly.common.function.ExceptionBiFunction;
import org.wildfly.common.function.ExceptionBiPredicate;
import org.wildfly.common.function.ExceptionConsumer;
import org.wildfly.common.function.ExceptionFunction;
import org.wildfly.common.function.ExceptionIntFunction;
import org.wildfly.common.function.ExceptionLongFunction;
import org.wildfly.common.function.ExceptionPredicate;
import org.wildfly.security.auth.client.AuthenticationContext;
import org.xnio.IoFuture;
import org.xnio.OptionMap;
import org.xnio.XnioWorker;

final class UncloseableEndpoint implements Endpoint {
    private final Endpoint endpoint;

    UncloseableEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public ContextManager<Endpoint> getInstanceContextManager() {
        return endpoint.getInstanceContextManager();
    }

    public static Endpoint getCurrent() {
        return Endpoint.getCurrent();
    }

    public String getName() {
        return endpoint.getName();
    }

    public Registration registerService(final String serviceType, final OpenListener openListener, final OptionMap optionMap) throws ServiceRegistrationException {
        return endpoint.registerService(serviceType, openListener, optionMap);
    }

    public IoFuture<Connection> getConnection(final URI destination) {
        return endpoint.getConnection(destination);
    }

    public IoFuture<Connection> connect(final URI destination) throws IOException {
        return endpoint.connect(destination);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions) throws IOException {
        return endpoint.connect(destination, connectOptions);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final SaslClientFactory saslClientFactory) throws IOException {
        return endpoint.connect(destination, connectOptions, saslClientFactory);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext) throws IOException {
        return endpoint.connect(destination, connectOptions, authenticationContext);
    }

    public IoFuture<Connection> connect(final URI destination, final OptionMap connectOptions, final AuthenticationContext authenticationContext, final SaslClientFactory saslClientFactory) throws IOException {
        return endpoint.connect(destination, connectOptions, authenticationContext, saslClientFactory);
    }

    public IoFuture<Connection> connect(final URI destination, final InetSocketAddress bindAddress, final OptionMap connectOptions, final AuthenticationContext authenticationContext, final SaslClientFactory saslClientFactory) throws IOException {
        return endpoint.connect(destination, bindAddress, connectOptions, authenticationContext, saslClientFactory);
    }

    public boolean isConnected(final URI uri) {
        return endpoint.isConnected(uri);
    }

    public Registration addConnectionProvider(final String uriScheme, final ConnectionProviderFactory providerFactory, final OptionMap optionMap) throws DuplicateRegistrationException, IOException {
        return endpoint.addConnectionProvider(uriScheme, providerFactory, optionMap);
    }

    public <T> T getConnectionProviderInterface(final String uriScheme, final Class<T> expectedType) throws UnknownURISchemeException, ClassCastException {
        return endpoint.getConnectionProviderInterface(uriScheme, expectedType);
    }

    public boolean isValidUriScheme(final String uriScheme) {
        return endpoint.isValidUriScheme(uriScheme);
    }

    public XnioWorker getXnioWorker() {
        return endpoint.getXnioWorker();
    }

    public static EndpointBuilder builder() {
        return Endpoint.builder();
    }

    public Key addCloseHandler(final CloseHandler<? super Endpoint> handler) {
        return endpoint.addCloseHandler(handler);
    }

    public Attachments getAttachments() {
        return endpoint.getAttachments();
    }

    public void run(final Runnable runnable) {
        endpoint.run(runnable);
    }

    public <R> R runAction(final PrivilegedAction<R> action) {
        return endpoint.runAction(action);
    }

    public <R> R runExceptionAction(final PrivilegedExceptionAction<R> action) throws PrivilegedActionException {
        return endpoint.runExceptionAction(action);
    }

    public <V> V runCallable(final Callable<V> callable) throws Exception {
        return endpoint.runCallable(callable);
    }

    public <T, U> void runBiConsumer(final BiConsumer<T, U> consumer, final T param1, final U param2) {
        endpoint.runBiConsumer(consumer, param1, param2);
    }

    public <T, U, E extends Exception> void runExBiConsumer(final ExceptionBiConsumer<T, U, E> consumer, final T param1, final U param2) throws E {
        endpoint.runExBiConsumer(consumer, param1, param2);
    }

    public <T> void runConsumer(final Consumer<T> consumer, final T param) {
        endpoint.runConsumer(consumer, param);
    }

    public <T, E extends Exception> void runExConsumer(final ExceptionConsumer<T, E> consumer, final T param) throws E {
        endpoint.runExConsumer(consumer, param);
    }

    public <T, U, R> R runBiFunction(final BiFunction<T, U, R> function, final T param1, final U param2) {
        return endpoint.runBiFunction(function, param1, param2);
    }

    public <T, U, R, E extends Exception> R runExBiFunction(final ExceptionBiFunction<T, U, R, E> function, final T param1, final U param2) throws E {
        return endpoint.runExBiFunction(function, param1, param2);
    }

    public <T, R> R runFunction(final Function<T, R> function, final T param) {
        return endpoint.runFunction(function, param);
    }

    public <T, R, E extends Exception> R runExFunction(final ExceptionFunction<T, R, E> function, final T param) throws E {
        return endpoint.runExFunction(function, param);
    }

    public <T, U> boolean runBiPredicate(final BiPredicate<T, U> predicate, final T param1, final U param2) {
        return endpoint.runBiPredicate(predicate, param1, param2);
    }

    public <T, U, E extends Exception> boolean runExBiPredicate(final ExceptionBiPredicate<T, U, E> predicate, final T param1, final U param2) throws E {
        return endpoint.runExBiPredicate(predicate, param1, param2);
    }

    public <T> boolean runPredicate(final Predicate<T> predicate, final T param) {
        return endpoint.runPredicate(predicate, param);
    }

    public <T, E extends Exception> boolean runExPredicate(final ExceptionPredicate<T, E> predicate, final T param) throws E {
        return endpoint.runExPredicate(predicate, param);
    }

    public <T> T runIntFunction(final IntFunction<T> function, final int value) {
        return endpoint.runIntFunction(function, value);
    }

    public <T, E extends Exception> T runExIntFunction(final ExceptionIntFunction<T, E> function, final int value) throws E {
        return endpoint.runExIntFunction(function, value);
    }

    public <T> T runLongFunction(final LongFunction<T> function, final long value) {
        return endpoint.runLongFunction(function, value);
    }

    public <T, E extends Exception> T runExLongFunction(final ExceptionLongFunction<T, E> function, final long value) throws E {
        return endpoint.runExLongFunction(function, value);
    }

    public void awaitClosedUninterruptibly() {
        endpoint.awaitClosedUninterruptibly();
    }

    public void awaitClosed() throws InterruptedException {
        endpoint.awaitClosed();
    }

    public void close() throws IOException {
        throw Assert.unsupported();
    }

    public void closeAsync() {
        throw Assert.unsupported();
    }
}

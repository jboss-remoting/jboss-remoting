package org.jboss.cx.remoting.spi.wrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.InterceptorDeploymentSpec;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceDeploymentSpec;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.EndpointShutdownListener;
import org.jboss.cx.remoting.spi.Discovery;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public class EndpointWrapper implements Endpoint {
    protected final Endpoint delegate;

    protected EndpointWrapper(final Endpoint endpoint) {
        delegate = endpoint;
    }

    public ConcurrentMap<Object, Object> getAttributes() {
        return delegate.getAttributes();
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public Session openSession(final EndpointLocator endpointLocator) throws RemotingException {
        return delegate.openSession(endpointLocator);
    }

    public Discovery discover(final String endpointName, final URI nextHop, final int cost) throws RemotingException {
        return delegate.discover(endpointName, nextHop, cost);
    }

    public Registration deployInterceptorType(final InterceptorDeploymentSpec spec) throws RemotingException {
        return delegate.deployInterceptorType(spec);
    }

    public String getName() {
        return delegate.getName();
    }

    public <I, O> Registration deployService(ServiceDeploymentSpec<I, O> spec) throws RemotingException, IllegalArgumentException {
        return delegate.deployService(spec);
    }

    public ProtocolRegistration registerProtocol(final ProtocolRegistrationSpec spec) throws RemotingException, IllegalArgumentException {
        return delegate.registerProtocol(spec);
    }

    public CallbackHandler getLocalCallbackHandler() {
        return delegate.getLocalCallbackHandler();
    }

    public CallbackHandler getRemoteCallbackHandler() {
        return delegate.getRemoteCallbackHandler();
    }

    public void setRemoteCallbackHandler(final CallbackHandler callbackHandler) {
        delegate.setRemoteCallbackHandler(callbackHandler);
    }

    public void setLocalCallbackHandler(final CallbackHandler callbackHandler) {
        delegate.setLocalCallbackHandler(callbackHandler);
    }

    public void addShutdownListener(final EndpointShutdownListener listener) {
        delegate.addShutdownListener(listener);
    }

    public void removeShutdownListener(final EndpointShutdownListener listener) {
        delegate.removeShutdownListener(listener);
    }
}

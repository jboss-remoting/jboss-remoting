package org.jboss.cx.remoting.spi.wrapper;

import java.net.URI;
import java.util.concurrent.ConcurrentMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.EndpointShutdownListener;
import org.jboss.cx.remoting.InterceptorDeploymentSpec;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceDeploymentSpec;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.spi.Discovery;
import org.jboss.cx.remoting.spi.Registration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistration;
import org.jboss.cx.remoting.spi.protocol.ProtocolRegistrationSpec;

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

    public Session openSession(final URI remoteUri, AttributeMap attributeMap) throws RemotingException {
        return delegate.openSession(remoteUri, attributeMap);
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

    public void addShutdownListener(final EndpointShutdownListener listener) {
        delegate.addShutdownListener(listener);
    }

    public void removeShutdownListener(final EndpointShutdownListener listener) {
        delegate.removeShutdownListener(listener);
    }
}

package org.jboss.cx.remoting.beans;

import java.net.URI;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.Session;

/**
 *
 */
public final class SessionBean {
    private URI destination;
    private AttributeMap attributeMap;
    private Endpoint endpoint;

    public URI getDestination() {
        return destination;
    }

    public void setDestination(final URI destination) {
        this.destination = destination;
    }

    public AttributeMap getAttributeMap() {
        return attributeMap;
    }

    public void setAttributeMap(final AttributeMap attributeMap) {
        this.attributeMap = attributeMap;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(final Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    // lifecycle

    private Session session;

    public void create() {

    }

    public void start() throws RemotingException {
        session = endpoint.openSession(destination, attributeMap);
    }

    public void stop() throws RemotingException {
        session.close();
    }

    public void destroy() {

    }
}

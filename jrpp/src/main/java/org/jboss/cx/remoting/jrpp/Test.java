package org.jboss.cx.remoting.jrpp;

import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.Remoting;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.EndpointLocator;
import org.jboss.cx.remoting.Session;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.io.IOException;

/**
 *
 */
public final class Test {
    public static void main(String[] args) throws RemotingException, IOException, URISyntaxException {
        Endpoint e = Remoting.createEndpoint("test01");
        final JrppProtocolSupport jrpp = new JrppProtocolSupport(e);
        jrpp.addServer(new InetSocketAddress(InetAddress.getLocalHost(), 4321));
        final Session session = e.openSession(EndpointLocator.DEFAULT.setEndpointUri(new URI("jrpp://localhost:4321")));
        System.out.println("Opened a session! -> " + session);
    }
}

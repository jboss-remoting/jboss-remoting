package org.jboss.cx.remoting.samples.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Security;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Remoting;
import org.jboss.cx.remoting.Session;
import org.jboss.cx.remoting.core.security.sasl.Provider;
import org.jboss.cx.remoting.util.AttributeMap;

/**
 *
 */
public final class JrppBasicExampleMain {

    public static void main(String[] args) throws IOException, RemoteExecutionException, URISyntaxException {
        Security.addProvider(new Provider());
        final StringRot13RequestListener listener = new StringRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple", listener);
        try {
            Remoting.addJrppServer(endpoint, new InetSocketAddress(12345), AttributeMap.EMPTY);
            Session session = endpoint.openSession(new URI("jrpp://localhost:12345"), AttributeMap.EMPTY);
            try {
                final Context<String,String> context = session.getRootContext();
                try {
                    final String original = "The Secret Message\n";
                    final String result = context.invoke(original);
                    System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), result.trim());
                } finally {
                    context.close();
                }
            } finally {
                session.close();
            }
        } finally {
            endpoint.close();
        }

    }
}
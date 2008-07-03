package org.jboss.cx.remoting.samples.simple;

import java.io.IOException;
import java.security.Security;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Remoting;
import org.jboss.cx.remoting.core.security.sasl.Provider;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class LocalBasicExampleMain {

    public static void main(String[] args) throws IOException, RemoteExecutionException {
        Security.addProvider(new Provider());
        final StringRot13RequestListener listener = new StringRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple");
        try {
            final Client<String,String> client = endpoint.createClient(listener).getClient();
            try {
                final String original = "The Secret Message\n";
                final String result = client.invoke(original);
                System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), result.trim());
            } finally {
                IoUtils.safeClose(client);
            }
        } finally {
            Remoting.closeEndpoint(endpoint);
        }
    }
}
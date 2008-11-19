package org.jboss.remoting.samples.simple;

import java.io.IOException;
import org.jboss.remoting.Client;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.Remoting;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class LocalBasicExampleMain {

    private LocalBasicExampleMain() {
    }

    public static void main(String[] args) throws IOException {
        final StringRot13RequestListener listener = new StringRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple");
        try {
            final Client<String,String> client = Remoting.createLocalClient(endpoint, listener, null, null);
            try {
                final String original = "The Secret Message\n";
                final String result = client.invoke(original);
                System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), result.trim());
            } finally {
                IoUtils.safeClose(client);
            }
        } finally {
            IoUtils.safeClose(endpoint);
        }
    }
}
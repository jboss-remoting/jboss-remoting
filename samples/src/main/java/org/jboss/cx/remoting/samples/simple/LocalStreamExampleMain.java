package org.jboss.cx.remoting.samples.simple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.security.Security;
import org.jboss.cx.remoting.Client;
import org.jboss.cx.remoting.Endpoint;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.Remoting;
import org.jboss.cx.remoting.core.security.sasl.Provider;

/**
 *
 */
public final class LocalStreamExampleMain {

    public static void main(String[] args) throws IOException, RemoteExecutionException {
        Security.addProvider(new Provider());
        final StreamingRot13RequestListener listener = new StreamingRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple", listener);
        try {
            final Client<Reader,Reader> client = endpoint.createClient(listener);
            try {
                final String original = "The Secret Message\n";
                final StringReader originalReader = new StringReader(original);
                try {
                    final Reader reader = client.send(originalReader).get();
                    try {
                        final BufferedReader bufferedReader = new BufferedReader(reader);
                        try {
                            final String secretLine = bufferedReader.readLine();
                            System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), secretLine);
                        } finally {
                            bufferedReader.close();
                        }
                    } finally {
                        reader.close();
                    }
                } finally {
                    originalReader.close();
                }
            } finally {
                client.close();
            }
        } finally {
            endpoint.close();
        }
    }
}

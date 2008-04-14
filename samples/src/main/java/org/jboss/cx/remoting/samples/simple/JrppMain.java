package org.jboss.cx.remoting.samples.simple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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
public final class JrppMain {

    public static void main(String[] args) throws IOException, RemoteExecutionException, URISyntaxException {
        Security.addProvider(new Provider());
        final Rot13RequestListener listener = new Rot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple", listener);
        try {
            Remoting.addJrppServer(endpoint, new InetSocketAddress(12345), AttributeMap.EMPTY);
            Session session = endpoint.openSession(new URI("jrpp://localhost:12345"), AttributeMap.EMPTY);
            try {
                final Context<Reader,Reader> context = session.getRootContext();
                try {
                    final String original = "The Secret Message\n";
                    final StringReader originalReader = new StringReader(original);
                    try {
                        final Reader reader = context.send(originalReader).get();
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

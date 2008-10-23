package org.jboss.remoting.samples.simple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import org.jboss.remoting.Client;
import org.jboss.remoting.Endpoint;
import org.jboss.remoting.Remoting;
import org.jboss.xnio.IoUtils;

/**
 *
 */
public final class LocalStreamExampleMain {

    private LocalStreamExampleMain() {
    }

    public static void main(String[] args) throws IOException {
        final StreamingRot13RequestListener listener = new StreamingRot13RequestListener();
        final Endpoint endpoint = Remoting.createEndpoint("simple");
        try {
            final Client<Reader,Reader> client = Remoting.createLocalClient(endpoint, listener);
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
                            IoUtils.safeClose(bufferedReader);
                        }
                    } finally {
                        IoUtils.safeClose(reader);
                    }
                } finally {
                    IoUtils.safeClose(originalReader);
                }
            } finally {
                IoUtils.safeClose(client);
            }
        } finally {
            Remoting.closeEndpoint(endpoint);
        }
    }
}

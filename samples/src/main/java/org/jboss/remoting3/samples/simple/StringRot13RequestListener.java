package org.jboss.remoting3.samples.simple;

import java.io.IOException;
import org.jboss.remoting3.RemoteExecutionException;
import org.jboss.remoting3.RequestContext;
import org.jboss.remoting3.RequestListener;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public final class StringRot13RequestListener implements RequestListener<String, String> {

    private static final Logger log = Logger.getLogger("jboss.example.string-rot-13");

    public void handleRequest(final RequestContext<String> readerRequestContext, final String request) throws RemoteExecutionException {
        log.info("Received request: \"%s\"", request);
        try {
            StringBuilder b = new StringBuilder(request.length());
            for (int i = 0; i < request.length(); i ++) {
                b.append(rot13(request.charAt(i)));
            }
            final String reply = b.toString();
            log.info("Sending reply: \"%s\"", reply);
            readerRequestContext.sendReply(reply);
        } catch (IOException e) {
            throw new RemoteExecutionException("Failed to send reply", e);
        }
    }

    public void handleClose() {
    }

    private char rot13(final char i) {
        if (i >= 'A' && i <= 'M' || i >= 'a' && i <= 'm') {
            return (char) (i + 13);
        } else if (i >= 'N' && i <= 'Z' || i >= 'n' && i <= 'z') {
            return (char) (i - 13);
        } else {
            return i;
        }
    }
}
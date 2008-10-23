package org.jboss.remoting.samples.simple;

import java.io.IOException;
import org.jboss.remoting.AbstractRequestListener;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.RequestContext;

/**
 *
 */
public final class StringRot13RequestListener extends AbstractRequestListener<String, String> {

    public void handleRequest(final RequestContext<String> readerRequestContext, final String request) throws RemoteExecutionException {
        try {
            StringBuilder b = new StringBuilder(request.length());
            for (int i = 0; i < request.length(); i ++) {
                b.append(rot13(request.charAt(i)));
            }
            readerRequestContext.sendReply(b.toString());
        } catch (IOException e) {
            throw new RemoteExecutionException("Failed to send reply", e);
        }
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
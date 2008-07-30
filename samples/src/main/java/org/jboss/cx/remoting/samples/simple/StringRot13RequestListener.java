package org.jboss.cx.remoting.samples.simple;

import org.jboss.cx.remoting.AbstractRequestListener;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RequestContext;
import java.io.IOException;

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
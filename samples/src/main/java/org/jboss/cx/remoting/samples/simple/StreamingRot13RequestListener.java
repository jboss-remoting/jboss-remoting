package org.jboss.cx.remoting.samples.simple;

import java.io.IOException;
import java.io.Reader;
import org.jboss.cx.remoting.AbstractRequestListener;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.RequestContext;

/**
 *
 */
public final class StreamingRot13RequestListener extends AbstractRequestListener<Reader, Reader> {

    public void handleRequest(final RequestContext<Reader> readerRequestContext, final Reader request) throws RemoteExecutionException {
        try {
            readerRequestContext.sendReply(new Reader() {

                public int read() throws IOException {
                    final int i = request.read();
                    if (i > 0) {
                        return rot13((char) i);
                    } else {
                        return i;
                    }
                }

                public int read(final char cbuf[], final int off, final int len) throws IOException {
                    for (int i = 0; i < len; i++) {
                        final int c = read();
                        if (c == -1) {
                            return i;
                        }
                        cbuf[off + i] = (char) c;
                    }
                    return len;
                }

                public void close() throws IOException {
                    request.close();
                }
            });
        } catch (RemotingException e) {
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

package org.jboss.cx.remoting.core.util;

import java.io.EOFException;
import java.io.IOException;

import javax.transaction.xa.XAException;

/**
 *
 */
public final class ExceptionUtil {
    private ExceptionUtil() {
    }

    public static IOException ioException(String msg, Exception wrap) {
        final String newMsg;
        final String wrapMessage = wrap.getMessage();
        if (msg == null) {
            if (wrapMessage == null) {
                newMsg = "No message";
            } else {
                newMsg = wrapMessage;
            }
        } else {
            if (wrapMessage == null) {
                newMsg = msg;
            } else {
                newMsg = msg + ": " + wrapMessage;
            }
        }
        IOException ioException = new IOException(newMsg);
        ioException.setStackTrace(wrap.getStackTrace());
        return ioException;
    }

    public static XAException xaException(String msg, Exception wrap) {
        final String newMsg;
        final String wrapMessage = wrap.getMessage();
        if (msg == null) {
            if (wrapMessage == null) {
                newMsg = "No message";
            } else {
                newMsg = wrapMessage;
            }
        } else {
            if (wrapMessage == null) {
                newMsg = msg;
            } else {
                newMsg = msg + ": " + wrapMessage;
            }
        }
        XAException xaException = new XAException(newMsg);
        xaException.setStackTrace(wrap.getStackTrace());
        return xaException;
    }

    public static EOFException eofException(String msg, Exception wrap) {
        final String newMsg;
        final String wrapMessage = wrap.getMessage();
        if (msg == null) {
            if (wrapMessage == null) {
                newMsg = "No message";
            } else {
                newMsg = wrapMessage;
            }
        } else {
            if (wrapMessage == null) {
                newMsg = msg;
            } else {
                newMsg = msg + ": " + wrapMessage;
            }
        }
        EOFException eofException = new EOFException(newMsg);
        eofException.setStackTrace(wrap.getStackTrace());
        return eofException;
    }
}

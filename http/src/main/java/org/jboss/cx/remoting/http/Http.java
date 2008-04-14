package org.jboss.cx.remoting.http;

/**
 *
 */
public final class Http {
    private Http() {}

    enum MessageType {
        SESSION_OPEN,
        SESSION_JOIN,
        SESSION_CLOSE,

        STREAM_OPEN,
        STREAM_CLOSE,
        STREAM_DATA,

        SERVICE_CLOSING,

        SERVICE_CLOSE,
        SERVICE_CONTEXT_OPEN,

        CONTEXT_CLOSE,

        CONTEXT_CLOSING,

        REQUEST,
        REQUEST_CANCEL,

        REQUEST_REPLY,
        REQUEST_EXCEPTION,
        REQUEST_CANCELLED,
    }
}

package org.jboss.cx.remoting.http;

/**
 *
 */
public final class Http {
    public static final String HEADER_SESSION_ID = "JBoss-Remoting-Session-ID";
    public static final String HEADER_ACK = "JBoss-Remoting-Ack";
    public static final String HEADER_SEQ = "JBoss-Remoting-Seq";

    private Http() {}

    enum MessageType {
        SESSION_OPEN,
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

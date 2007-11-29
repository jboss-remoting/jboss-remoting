package org.jboss.cx.remoting.core;

/**
 *
 */
public final class CoreProtocolMessage {
    public enum Type {
        REQUEST,
        REPLY,
        MESSAGE,
        EXCEPTION,

    }

    private final Type type;
    private final Object identifier;
    private final Object body;

    public CoreProtocolMessage(final Type type, final Object identifier, final Object body) {
        this.type = type;
        this.identifier = identifier;
        this.body = body;
    }

    public Type getType() {
        return type;
    }

    public Object getIdentifier() {
        return identifier;
    }

    public Object getBody() {
        return body;
    }
}

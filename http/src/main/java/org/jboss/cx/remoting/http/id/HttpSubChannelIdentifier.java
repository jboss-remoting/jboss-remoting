package org.jboss.cx.remoting.http.id;

/**
 *
 */
public abstract class HttpSubChannelIdentifier {
    private final long id;

    protected HttpSubChannelIdentifier(long id) {
        this.id = id;
    }

    public int hashCode() {
        return (int) id;
    }

    public boolean equals(Object obj) {
        return obj instanceof HttpSubChannelIdentifier && ((HttpSubChannelIdentifier)obj).id == id;
    }

    public String toString() {
        return Long.toHexString(id);
    }
}

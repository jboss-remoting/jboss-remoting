package org.jboss.cx.remoting.http.cookie;

import java.io.Serializable;

/**
 * C is for: a simple HTTP cookie class, for HTTP transports that have no cookie handling.  It's good enough for me.
 */
public final class Cookie implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String value;
    private final String name;
    private final String path;
    private final CookieDomain domain;
    private final long expires;
    private final boolean secure;
    private final Key key;

    public Cookie(final String name, final String value, final String path, final CookieDomain domain, final long expires, final boolean secure) {
        this.expires = expires;
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        if (value == null) {
            throw new NullPointerException("value is null");
        }
        if (path == null) {
            throw new NullPointerException("path is null");
        }
        if (domain == null) {
            throw new NullPointerException("domain is null");
        }
        this.name = name;
        this.value = value;
        this.path = path;
        this.domain = domain;
        this.secure = secure;
        key = new Key(name, path);
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public String getPath() {
        return path;
    }

    public CookieDomain getDomain() {
        return domain;
    }

    public long getExpires() {
        return expires;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean equals(Cookie other) {
        return this == other || name.equals(other.name) && value.equals(other.value) && path.equals(other.path) && domain.equals(other.domain) && secure == other.secure;
    }

    public boolean equals(Object other) {
        return other instanceof Cookie && equals((Cookie) other);
    }

    public int hashCode() {
        return 31 * (31 * (31 * (31 * name.hashCode() + value.hashCode()) + path.hashCode()) + domain.hashCode()) + (secure ? 1 : 0);
    }

    public boolean isExpired() {
        return expires != 0L && System.currentTimeMillis() > expires;
    }

    public Key getKey() {
        return key;
    }

    public static final class Key implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String name;
        private final String path;

        public Key(final String name, final String path) {
            if (name == null) {
                throw new NullPointerException("name is null");
            }
            if (path == null) {
                throw new NullPointerException("path is null");
            }
            this.name = name;
            this.path = path;
        }

        public int hashCode() {
            return 31 * path.hashCode() + name.hashCode();
        }

        private String getName() {
            return name;
        }

        private String getPath() {
            return path;
        }

        public boolean equals(final Key other) {
            return this == other || name.equals(other.getName()) && path.equals(other.getPath());
        }

        public boolean equals(final Object other) {
            return super.equals(other);
        }
    }
}

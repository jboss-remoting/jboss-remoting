package org.jboss.cx.remoting.http.cookie;

import java.io.Serializable;
import java.util.Arrays;
import org.jboss.cx.remoting.util.CollectionUtil;
import static org.jboss.cx.remoting.util.CollectionUtil.split;
import static org.jboss.cx.remoting.util.CollectionUtil.toArrayReversed;

/**
 *
 */
public final class CookieDomain implements Serializable {

    public static final CookieDomain LOCAL = new CookieDomain(".local");

    private static final long serialVersionUID = 1L;

    private final String[] parts;
    private final boolean hostDomainName;

    private CookieDomain(String[] parts, boolean hostDomainName) {
        this.parts = parts;
        this.hostDomainName = hostDomainName;
    }

    public CookieDomain(String domain) {
        if (domain == null) {
            throw new NullPointerException("domain is null");
        }
        if (domain.length() == 0) {
            throw new IllegalArgumentException("domain is empty");
        }
        hostDomainName = domain.charAt(0) == '.';
        final String baseDomain = hostDomainName ? domain.substring(1).toLowerCase() : domain.toLowerCase();
        parts = toArrayReversed(split(".", baseDomain).iterator(), String.class);
    }

    public boolean equals(final CookieDomain other) {
        return other != null && hostDomainName == other.hostDomainName && Arrays.equals(parts, other.parts);
    }

    public boolean equals(final Object other) {
        return other instanceof CookieDomain && equals((CookieDomain)other);
    }

    public int hashCode() {
        return Arrays.hashCode(parts) + (hostDomainName ? 1 : 0);
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder(40);
        builder.append("Domain: ");
        for (String x : parts) {
            builder.append(x);
            builder.append('/');
        }
        builder.setLength(builder.length() - 1);
        return builder.toString();
    }

    public boolean matches(final CookieDomain other) {
        // todo this doesn't quite match rfc 2965
        return other.hostDomainName ? CollectionUtil.arrayStartsWith(parts, other.parts) : Arrays.equals(other.parts, parts); 
    }

    public int getPartCount() {
        return parts.length;
    }

    public String getPart(int index) {
        return parts[index];
    }

    public boolean hasParent() {
        return parts.length > 1;
    }

    public boolean isHostDomainName() {
        return hostDomainName;
    }

    public CookieDomain getParent() {
        final String[] parentParts = new String[parts.length - 1];
        System.arraycopy(parts, 0, parentParts, 0, parentParts.length);
        return new CookieDomain(parentParts, false);
    }
}

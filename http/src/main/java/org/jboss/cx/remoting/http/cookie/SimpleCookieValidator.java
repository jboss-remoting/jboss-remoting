package org.jboss.cx.remoting.http.cookie;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Simple cookie validator.  Validates a cookie coming down from a server.  See
 * http://wp.netscape.com/newsref/std/cookie_spec.html and http://www.ietf.org/rfc/rfc2965.txt
 * for more info.
 */
public final class SimpleCookieValidator implements CookieValidator {
    private static final Set<String> TLD_SET;

    static {
        final HashSet<String> tldSet = new HashSet<String>();
        tldSet.add("com");
        tldSet.add("edu");
        tldSet.add("net");
        tldSet.add("org");
        tldSet.add("gov");
        tldSet.add("mil");
        tldSet.add("int");
        TLD_SET = Collections.unmodifiableSet(tldSet);
    }

    public boolean isValid(final Cookie cookie, final CookieDomain requestDomain) {
        final CookieDomain cookieDomain = cookie.getDomain();
        for (int i = 0; i < cookieDomain.getPartCount(); i++) {
            if (cookieDomain.getPart(i).length() == 0) {
                return false;
            }
        }
        final int numParts = cookieDomain.getPartCount() + (cookieDomain.isHostDomainName() ? 1 : 0);
        final String tld = numParts == 0 ? null : cookieDomain.getPart(0);
        final int minSegments = TLD_SET.contains(tld) ? 3 : 4;
        if (numParts < minSegments) {
            // not valid: domain name is too short
            return false;
        }
        final String path = cookie.getPath();
        if (path.length() == 0 || path.charAt(0) != '/') {
            // not valid: bad or missing path
            return false;
        }
        if (! requestDomain.matches(cookieDomain)) {
            // wrong domain
            return false;
        }
        return true;
    }
}

package org.jboss.cx.remoting.http.cookie;

/**
 * Simple cookie matcher.  See http://wp.netscape.com/newsref/std/cookie_spec.html
 */
public final class SimpleCookieMatcher implements CookieMatcher {

    public boolean matches(final Cookie cookie, final CookieDomain requestDomain, final String path, final boolean secure) {
        final boolean cookieSecure = cookie.isSecure();
        if (cookieSecure && ! secure) {
            return false;
        }
        final CookieDomain cookieDomain = cookie.getDomain();
        final String cookiePath = cookie.getPath();
        return requestDomain.matches(cookieDomain) && path.startsWith(cookiePath) && !cookie.isExpired();
    }
}

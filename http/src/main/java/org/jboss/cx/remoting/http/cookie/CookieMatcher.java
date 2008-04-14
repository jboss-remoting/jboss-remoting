package org.jboss.cx.remoting.http.cookie;

/**
 * Cookie matcher.  Used to determine if a cookie should be sent to a server.
 */
public interface CookieMatcher {

    /**
     * Determine whether a cookie matches a server.
     *
     * @param cookie the cookie
     * @param requestDomain the domain that the request is being sent to
     * @param path the path that the request is being sent to
     * @param secure whether the request is on a secure channel
     * @return {@code true} if the cookie should be sent
     */
    boolean matches(Cookie cookie, CookieDomain requestDomain, String path, boolean secure);
}

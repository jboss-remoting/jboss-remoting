package org.jboss.cx.remoting.http.cookie;

/**
 * Cookie validator.  Used to validate cookies sent to the client from the server.
 */
public interface CookieValidator {

    /**
     * Determine whether a cookie from a server is valid.
     *
     * @param cookie the cookie from the server
     * @param requestDomain the domain that the request was sent to
     * @return {@code true} if the cookie is valid
     */
    boolean isValid(Cookie cookie, String requestDomain);
}

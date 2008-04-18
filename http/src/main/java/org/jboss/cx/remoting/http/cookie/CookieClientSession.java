package org.jboss.cx.remoting.http.cookie;

import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jboss.cx.remoting.log.Logger;
import static org.jboss.cx.remoting.util.CollectionUtil.synchronizedHashMap;

/**
 *
 */
public final class CookieClientSession {
    private static final Logger log = Logger.getLogger(CookieClientSession.class);

    private final CookieMatcher cookieMatcher;
    private final CookieValidator cookieValidator;
    private final CookieParser cookieParser;

    private final Map<Cookie.Key, Cookie> cookieMap = synchronizedHashMap();

    public CookieClientSession(final CookieMatcher cookieMatcher, final CookieValidator cookieValidator, final CookieParser cookieParser) {
        this.cookieMatcher = cookieMatcher;
        this.cookieValidator = cookieValidator;
        this.cookieParser = cookieParser;
    }

    public CookieClientSession() {
        cookieMatcher = new SimpleCookieMatcher();
        cookieValidator = new SimpleCookieValidator();
        cookieParser = new SimpleCookieParser();
    }

    /**
     * Get a cookie header for this session, given a request domain and path.  Follows all the silly rules, like being
     * sorted by path length.
     *
     * @param domain the request domain
     * @param path the request path (sans file, with trailing slash)
     * @param secureRequest {@code true} if the request will use the {@code https} protocol
     * @return the cookie header value
     */
    public String getCookieHeader(String domain, String path, boolean secureRequest) {
        final SortedMap<Cookie.Key, Cookie> sortedValidatedCookies = new TreeMap<Cookie.Key, Cookie>();
        for (final Cookie cookie : cookieMap.values()) {
            if (cookieMatcher.matches(cookie, domain, path, secureRequest)) {
                sortedValidatedCookies.put(cookie.getKey(), cookie);
            }
        }
        final StringBuilder builder = new StringBuilder();
        final Iterator<Cookie> it = sortedValidatedCookies.values().iterator();
        while (it.hasNext()) {
            Cookie cookie = it.next();
            builder.append(cookie.getName()).append('=').append(cookie.getValue());
            if (it.hasNext()) builder.append("; ");
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    /**
     * Handle a Set-Cookie type of header from the server.
     *
     * @param headerValue the text of the header
     * @param domain the request domain
     * @param path the request path
     */
    public void handleSetCookieHeader(String headerValue, String domain, String path) {
        final Cookie cookie = cookieParser.parseSetCookie(headerValue, domain, path);
        if (! cookieValidator.isValid(cookie, domain)) {
            log.trace("Ignoring invalid cookie %s", cookie);
        } else {
            log.trace("Adding cookie '%s' from domain '%s'", cookie, domain);
            cookieMap.put(cookie.getKey(), cookie);
        }
    }
}

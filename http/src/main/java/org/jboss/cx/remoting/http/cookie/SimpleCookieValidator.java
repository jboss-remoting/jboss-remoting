package org.jboss.cx.remoting.http.cookie;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jboss.cx.remoting.log.Logger;

/**
 * Simple cookie validator.  Validates a cookie coming down from a server.  See
 * http://wp.netscape.com/newsref/std/cookie_spec.html and http://www.ietf.org/rfc/rfc2965.txt
 * for more info.
 */
public final class SimpleCookieValidator implements CookieValidator {
    private static final String DOMAIN_PATTERN_STRING = "^(?:(?:[a-zA-Z0-9][a-zA-Z0-9]+)(?:-(?:[a-zA-Z0-9][a-zA-Z0-9]+))*(?:\\.(?:(?:[a-zA-Z0-9][a-zA-Z0-9]+)(?:-(?:[a-zA-Z0-9][a-zA-Z0-9]+))*)+$";
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(DOMAIN_PATTERN_STRING);

    private static final String COOKIE_PATTERN_STRING = "^([^=;,\\p{Space}]*)$";
    private static final Pattern COOKIE_PATTERN = Pattern.compile(COOKIE_PATTERN_STRING);

    private static final Set<String> TLD_SET;

    private static final Logger log = Logger.getLogger(SimpleCookieValidator.class);

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

    private static void logReject(Cookie cookie, String requestDomain, String reason) {
        log.trace("Rejecting cookie \"%s\" from request domain \"%s\": %s", cookie.getName(), requestDomain, reason);
    }

    public boolean isValid(final Cookie cookie, final String requestDomain) {

        final String cookieDomain = cookie.getDomain();
        final String matchDomain;
        if (cookieDomain.length() == 0) {
            logReject(cookie, requestDomain, "cookie domain length is zero");
            return false;
        }
        if (cookieDomain.charAt(0) == '.') {
            matchDomain = cookieDomain.substring(1);
        } else {
            matchDomain = cookieDomain;
        }
        if (! DOMAIN_PATTERN.matcher(matchDomain).matches()) {
            logReject(cookie, requestDomain, "cookie has an invalid domain");
            return false;
        }
        final String effectiveDomain;
        if (matchDomain.indexOf('.') == -1) {
            effectiveDomain = matchDomain + ".local";
        } else {
            effectiveDomain = matchDomain;
        }
        final String tld = effectiveDomain.substring(effectiveDomain.lastIndexOf('.') + 1);
        final int minDots = TLD_SET.contains(tld) ? 1 : 2;
        int dotCount = 0;
        for (int p = effectiveDomain.indexOf('.', 0); p != -1; p = effectiveDomain.indexOf('.', p + 1)) {
            dotCount ++;
        }
        if (dotCount < minDots) {
            logReject(cookie, requestDomain, "cookie domain name is too short (see http://wp.netscape.com/newsref/std/cookie_spec.html)");
            return false;
        }
        final String path = cookie.getPath();
        if (path.length() == 0 || path.charAt(0) != '/') {
            logReject(cookie, requestDomain, "cookie path is invalid");
            return false;
        }
        final String name = cookie.getName();
        if (! COOKIE_PATTERN.matcher(name).matches()) {
            logReject(cookie, requestDomain, "cookie name is invalid");
        }
        final String value = cookie.getValue();
        if (! COOKIE_PATTERN.matcher(value).matches()) {
            logReject(cookie, requestDomain, "cookie value is invalid");
        }
        log.trace("Accepting cookie \"%s\" from request domain \"%s\"", name, requestDomain);
        return true;
    }
}

package org.jboss.cx.remoting.http.cookie;

/**
 *
 */
public interface CookieParser {
    Cookie parseSetCookie(String setCookie, String defaultDomain, String defaultPath);
}

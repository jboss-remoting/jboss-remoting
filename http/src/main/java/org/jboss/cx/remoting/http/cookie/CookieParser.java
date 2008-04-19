package org.jboss.cx.remoting.http.cookie;

import java.util.List;

/**
 *
 */
public interface CookieParser {
    Cookie parseSetCookie(String setCookie, String defaultDomain, String defaultPath);

    List<Cookie> parseCookie(String cookie);
}

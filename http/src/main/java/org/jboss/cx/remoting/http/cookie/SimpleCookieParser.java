package org.jboss.cx.remoting.http.cookie;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.CollectionUtil;

/**
 *
 */
public final class SimpleCookieParser implements CookieParser {

    private static final String DATE_FORMAT = "EEE, dd-MMM-yyyy HH:mm:ss";

    private static final Logger log = Logger.getLogger(SimpleCookieParser.class);

    private static final class Pair {

        private final String name;
        private final String value;

        private Pair(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
    }

    public Cookie[] parseSetCookie(final String setCookie, final CookieDomain defaultDomain, final String defaultPath) {
        if (setCookie == null) {
            throw new NullPointerException("setCookie is null");
        }
        if (defaultDomain == null) {
            throw new NullPointerException("defaultDomain is null");
        }
        if (defaultPath == null) {
            throw new NullPointerException("defaultPath is null");
        }
        boolean secure = false;
        long expires = 0L;
        CookieDomain domain = defaultDomain;
        String path = defaultPath;
        List<Pair> pairs = CollectionUtil.arrayList();
        for (final String s : CollectionUtil.split(";", setCookie)) {
            final String assignment = s.trim();
            final int equalsPos = assignment.indexOf('=');
            if (equalsPos == -1) {
                if (assignment.toLowerCase().equals("secure")) {
                    secure = true;
                    continue;
                }
            } else {
                String name = assignment.substring(0, equalsPos).trim();
                String lowerName = name.toLowerCase();
                String value = assignment.substring(equalsPos + 1).trim();
                if (lowerName.equals("expires")) {
                    final int gmti = value.lastIndexOf(" GMT");
                    if (gmti != -1) {
                        value = value.substring(0, gmti);
                    }
                    final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
                    try {
                        expires = dateFormat.parse(value).getTime();
                    } catch (ParseException e) {
                        log.trace("Invalid cookie expiration date '%s'", value);
                    }
                } else if (lowerName.equals("domain")) {
                    domain = new CookieDomain(value);
                } else if (lowerName.equals("path")) {
                    path = value;
                } else {
                    pairs.add(new Pair(name, value));
                }
            }
        }
        Cookie[] cookies = new Cookie[pairs.size()];
        int i = 0;
        for (Pair pair : pairs) {
            cookies[i++] = new Cookie(pair.name, pair.value, path, domain, expires, secure);
        }
        return cookies;
    }
}

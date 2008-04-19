package org.jboss.cx.remoting.http.cookie;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.util.CollectionUtil;
import static org.jboss.cx.remoting.util.CollectionUtil.arrayList;

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

    private static final String PAIR_PATTERN_STRING = "(\\s*+[^=;]*?)(?:\\s+=\\s*+([^;]*?)\\s+)(?:;|$)";
    private static final Pattern PAIR_PATTERN = Pattern.compile(PAIR_PATTERN_STRING);

    public Cookie parseSetCookie(final String setCookie, final String defaultDomain, final String defaultPath) {
        if (setCookie == null) {
            throw new NullPointerException("setCookie is null");
        }
        if (defaultDomain == null) {
            throw new NullPointerException("defaultDomain is null");
        }
        if (defaultPath == null) {
            throw new NullPointerException("defaultPath is null");
        }
        final Matcher matcher = PAIR_PATTERN.matcher(setCookie);
        if (! matcher.find()) {
            return null; // no cookie!
        }
        final String name = matcher.group(1);
        final String value = matcher.group(2);
        if (name == null || value == null) {
            return null; // no cookie!
        }
        boolean secure = false;
        long expires = 0L;
        String path = defaultPath;
        String domain = defaultDomain;
        while (matcher.find()) {
            final String attrName = matcher.group(1);
            final String attrValue = matcher.group(2);
            if ("secure".equalsIgnoreCase(attrName) && attrValue == null) {
                secure = true;
            } else if ("expires".equalsIgnoreCase(attrName) && attrValue != null) {
                final int gmti = value.lastIndexOf(" GMT");
                final String dateValue;
                if (gmti != -1) {
                    dateValue = attrValue.substring(0, gmti);
                } else {
                    dateValue = attrValue;
                }
                final SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
                try {
                    expires = dateFormat.parse(dateValue).getTime();
                } catch (ParseException e) {
                    log.trace("Invalid cookie expiration date '%s'", value);
                }
            } else if ("domain".equalsIgnoreCase(attrName) && attrValue != null) {
                domain = attrValue;
            } else if ("path".equalsIgnoreCase(attrName) && attrValue != null) {
                path = attrValue;
            } else {
                log.trace("Unknown cookie attribute-value pair: \"%s\"=\"%s\"", attrName, attrValue);
            }
        }
        return new Cookie(name, value, path, domain, expires, secure);
    }

    public List<Cookie> parseCookie(final String cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie is null");
        }
        List<Cookie> cookieList = arrayList();
        final Matcher matcher = PAIR_PATTERN.matcher(cookie);
        while (matcher.find()) {
            final String name = matcher.group(1);
            final String value = matcher.group(2);
            if (name != null && value != null) {
                cookieList.add(new Cookie(name, value, "/", ".unknown.local", -1L, false));
            }
        }
        return cookieList;
    }
}

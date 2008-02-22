package org.jboss.cx.remoting.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public final class ServiceURI {
    public static final String SCHEME = "jrs";

    private static final String FIRST_CHAR = "[$_a-zA-Z]";
    private static final String SUBSEQUENT_CHAR = "[-+$_a-zA-Z0-9]*";
    private static final String ID = FIRST_CHAR + SUBSEQUENT_CHAR;
    private static final String SEPARATOR = "[./]";

    private static final Pattern VALID_PATTERN = Pattern.compile("^(?:" + ID + "(?:" + SEPARATOR + ID + ")*)*$");

    private final URI uri;
    private final String serviceType;
    private final String groupName;
    private final String endpointName;

    public ServiceURI(final String str) throws URISyntaxException {
        this(new URI(str));
    }

    public ServiceURI(final URI uri) {
        this.uri = uri;
        if (! uri.getScheme().equals(SCHEME)) {
            throw new IllegalArgumentException("Invalid URI scheme for service");
        }
        final String ssp = uri.getSchemeSpecificPart();
        final int stcp = ssp.indexOf(':');
        if (stcp == -1) {
            serviceType = ssp;
            groupName = "";
            endpointName = "";
        } else {
            serviceType = ssp.substring(0, stcp).trim();
            final int gncp = ssp.indexOf(':', stcp + 1);
            if (gncp == -1) {
                groupName = ssp.substring(stcp + 1).trim();
                endpointName = "";
            } else {
                groupName = ssp.substring(stcp + 1, gncp).trim();
                // ignore everything after the last :
                final int encp = ssp.indexOf(':', gncp + 1);
                if (encp == -1) {
                    endpointName = ssp.substring(gncp + 1).trim();
                } else {
                    endpointName = ssp.substring(gncp + 1, encp).trim();
                }
            }
        }
        final Matcher matcher = VALID_PATTERN.matcher(serviceType);
        if (! matcher.matches()) {
            throw new IllegalArgumentException("Syntax error in service type URI part");
        }
        matcher.reset(groupName);
        if (! matcher.matches()) {
            throw new IllegalArgumentException("Syntax error in group name URI part");
        }
        matcher.reset(endpointName);
        if (! matcher.matches()) {
            throw new IllegalArgumentException("Syntax error in endpoint name URI part");
        }
    }

    public URI getUri() {
        return uri;
    }

    public String getServiceType() {
        return serviceType;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getEndpointName() {
        return endpointName;
    }
}

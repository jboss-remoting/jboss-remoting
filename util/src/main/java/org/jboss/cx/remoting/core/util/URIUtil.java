package org.jboss.cx.remoting.core.util;

import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 */
public final class URIUtil {
    private URIUtil() { /* empty */ }

    public static String getAbsolutePath(URI baseUri, String relativePath) {
        try {
            return getAbsolutePath(baseUri, new URI(null, null, relativePath, null));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static String getAbsolutePath(URI baseUri, URI relativePath) {
        return baseUri.resolve(relativePath).getPath();
    }

}

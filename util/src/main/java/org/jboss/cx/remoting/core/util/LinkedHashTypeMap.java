package org.jboss.cx.remoting.core.util;

import java.util.LinkedHashMap;

/**
 *
 */
public final class LinkedHashTypeMap<B> extends AbstractTypeMap<B> {
    public LinkedHashTypeMap() {
        super(new LinkedHashMap<Class<? extends B>, B>());
    }
}

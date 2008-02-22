package org.jboss.cx.remoting.util;

import java.util.HashMap;

/**
 *
 */
public final class HashTypeMap<B> extends AbstractTypeMap<B> {
    public HashTypeMap() {
        super(new HashMap<Class<? extends B>, B>());
    }
}

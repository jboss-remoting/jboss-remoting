package org.jboss.cx.remoting.service;

import java.io.Serializable;

/**
 *
 */
public final class ClassRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String name;

    public ClassRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}

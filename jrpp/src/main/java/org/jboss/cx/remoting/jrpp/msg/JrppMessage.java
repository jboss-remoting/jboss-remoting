package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;

/**
 *
 */
public abstract class JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    protected JrppMessage() {
    }

    public abstract void accept(JrppMessageVisitor visitor);
}

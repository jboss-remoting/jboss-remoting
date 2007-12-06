package org.jboss.cx.remoting.jrpp.msg;

import java.io.Serializable;
import java.io.IOException;
import java.io.ObjectInputStream;

/**
 *
 */
public abstract class JrppMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    protected JrppMessage() {
    }

    protected JrppMessage(ObjectInputStream ois) throws IOException {
    }

    public abstract void accept(JrppMessageVisitor visitor);
}

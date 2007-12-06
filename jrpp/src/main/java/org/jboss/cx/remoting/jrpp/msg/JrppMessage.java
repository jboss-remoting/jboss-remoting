package org.jboss.cx.remoting.jrpp.msg;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.jboss.cx.remoting.jrpp.WritableObject;

/**
 *
 */
public abstract class JrppMessage implements WritableObject {

    protected JrppMessage() {
    }

    protected JrppMessage(ObjectInputStream ois) throws IOException {
    }

    public void writeObjectData(ObjectOutputStream oos) throws IOException {
    }

    public abstract void accept(JrppMessageVisitor visitor);
}

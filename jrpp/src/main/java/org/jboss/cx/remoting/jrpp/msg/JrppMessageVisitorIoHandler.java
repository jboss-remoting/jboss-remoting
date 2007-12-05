package org.jboss.cx.remoting.jrpp.msg;

import org.apache.mina.handler.multiton.SingleSessionIoHandler;

/**
 *
 */
public abstract class JrppMessageVisitorIoHandler implements SingleSessionIoHandler, JrppMessageVisitor {
    public final void messageReceived(Object object) {
        ((JrppMessage)object).accept(this);
    }
}

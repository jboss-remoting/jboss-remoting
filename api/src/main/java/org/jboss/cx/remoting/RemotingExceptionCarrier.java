package org.jboss.cx.remoting;

/**
 *
 */
public class RemotingExceptionCarrier extends IOExceptionCarrier {
    public RemotingExceptionCarrier(RemotingException cause) {
        super(cause);
    }

    public RemotingException getCause() {
        return (RemotingException) super.getCause();
    }
}

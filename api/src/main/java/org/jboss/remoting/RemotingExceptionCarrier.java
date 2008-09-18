package org.jboss.remoting;

/**
 * A runtime exception that carries a {@link org.jboss.remoting.RemotingException} as a cause.
 */
public class RemotingExceptionCarrier extends IOExceptionCarrier {

    private static final long serialVersionUID = -1326735788761007331L;

    /**
     * Construct a new carrier.
     *
     * @param cause the nested cause
     */
    public RemotingExceptionCarrier(RemotingException cause) {
        super(cause);
    }

    /**
     * Get the cause.
     *
     * @return the cause
     */
    public RemotingException getCause() {
        return (RemotingException) super.getCause();
    }
}

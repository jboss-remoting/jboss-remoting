package org.jboss.cx.remoting.spi;

/**
 * Represents a locally-managed transaction.  Instances of this interface are created when a local transaction is
 * suspended via the {@code SessionHandler}, and are passed back in to the {@code SessionHandler} when resuming a
 * transaction.
 */
public interface SessionLocalTransaction {
}

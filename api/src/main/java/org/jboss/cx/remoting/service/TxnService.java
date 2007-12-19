package org.jboss.cx.remoting.service;

import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.spi.ContextService;

import javax.transaction.xa.XAResource;

/**
 *
 */
public interface TxnService extends ContextService {
    /**
     * Begin a transaction on the remote side.
     *
     * @throws org.jboss.cx.remoting.RemotingException if the transaction could not be started
     */
    void begin() throws RemotingException;

    /**
     * Commit the current transaction on the remote side.
     *
     * @throws RemotingException if the transaction could not be committed.
     */
    void commit() throws RemotingException;

    /**
     * Roll back the current transaction on the remote side.
     *
     * @throws RemotingException if the transaction could not be rolled back
     */
    void rollback() throws RemotingException;

    /**
     * Get an XA resource to control transactions on the remote side for this context.
     *
     * @return the XA resource
     *
     * @throws RemotingException if the XA resource could not be acquired
     */
    XAResource getXAResource() throws RemotingException;
}

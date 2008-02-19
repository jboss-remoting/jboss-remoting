package org.jboss.cx.remoting.service;

import org.jboss.cx.remoting.RemotingException;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface SecurityService {

    /**
     * @param userName
     *
     * @throws RemotingException
     */
    void changeUser(String userName) throws RemotingException;

    /**
     * @param userName
     * @param password
     *
     * @throws RemotingException
     */
    void changeUser(String userName, char[] password) throws RemotingException;

    /**
     * @param clientCallbackHandler
     *
     * @throws RemotingException
     */
    void changeUser(CallbackHandler clientCallbackHandler) throws RemotingException;

}

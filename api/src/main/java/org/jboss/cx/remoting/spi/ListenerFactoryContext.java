package org.jboss.cx.remoting.spi;

import org.jboss.cx.remoting.RemotingException;

/**
 *
 */
public interface ListenerFactoryContext {
    /**
     * Add an interceptor that the remote side is required to acknowledge.
     *
     * @param name
     */
    <T> T requireInterceptor(String name, Class<T> interceptorType) throws RemotingException;

    /**
     * Add an interceptor if the remote side agrees.
     *
     * @param name
     */
    <T> T offerInterceptor(String name, Class<T> interceptorType) throws RemotingException;

    /**
     * Add an interceptor to the local side.  The remote side is not notified.
     *
     * @param name
     */
    <T> T addPrivateInterceptor(String name, Class<T> interceptorType) throws RemotingException;


}

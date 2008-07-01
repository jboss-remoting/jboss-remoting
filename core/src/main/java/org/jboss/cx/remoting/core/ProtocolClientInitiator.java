package org.jboss.cx.remoting.core;

/**
 *
 */
public interface ProtocolClientInitiator<I, O> extends ClientInitiator {
    RequestInitiator<O> addRequest(RequestIdentifier requestIdentifier);

}

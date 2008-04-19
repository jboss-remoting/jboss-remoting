package org.jboss.cx.remoting.http;

/**
 *
 */
public interface RemotingHttpSessionContext {

    /**
     * Get a channel context that can be used to transport HTTP messages for this session.
     *
     * @return the channel context
     */
    RemotingHttpChannelContext getChannelContext();
}

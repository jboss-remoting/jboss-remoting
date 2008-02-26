package org.jboss.cx.remoting;

import java.io.IOException;
import java.net.URI;
import org.jboss.cx.remoting.util.AttributeHashMap;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.wrapper.ContextSourceWrapper;
import org.jboss.cx.remoting.spi.wrapper.SessionWrapper;
import org.jboss.cx.remoting.core.CoreEndpoint;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 *
 */
public final class Remoting {
    private static final Logger log = Logger.getLogger(Remoting.class);

    public static Endpoint createEndpoint(String name) {
        return null;
    }

    public static Session createEndpointAndSession(String endpointName, URI remoteUri, final String userName, final char[] password) throws RemotingException {
        return null;
    }

    public static <I, O> ContextSource<I, O> createEndpointAndOpenService(String endpointName, URI remoteUri, String userName, char[] password, Class<I> requestType, Class<O> replyType, String serviceType, String serviceGroupName) throws RemotingException {
        return null;
    }

    // privates

    private Remoting() { /* empty */ }
}

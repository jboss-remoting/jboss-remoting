package org.jboss.cx.remoting;

import java.io.IOException;
import java.net.URI;
import org.jboss.cx.remoting.core.CoreEndpointProvider;
import org.jboss.cx.remoting.util.AttributeHashMap;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.EndpointProvider;
import org.jboss.cx.remoting.spi.wrapper.ContextSourceWrapper;
import org.jboss.cx.remoting.spi.wrapper.SessionWrapper;

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

    private static final class EndpointProviderHolder {
        private static final EndpointProvider provider = new CoreEndpointProvider();
    }

    public static Endpoint createEndpoint(String name) {
        return EndpointProviderHolder.provider.createEndpoint(name);
    }

    public static Session createEndpointAndSession(String endpointName, URI remoteUri, final String userName, final char[] password) throws RemotingException {
        final Endpoint endpoint = createEndpoint(endpointName);
        boolean ok = false;
        final CallbackHandler callbackHandler = new CallbackHandler() {
            public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
                for (Callback callback : callbacks) {
                    if (callback instanceof NameCallback) {
                        ((NameCallback)callback).setName(userName);
                    } else if (callback instanceof PasswordCallback) {
                        ((PasswordCallback)callback).setPassword(password);
                    } else {
                        throw new UnsupportedCallbackException(callback);
                    }
                }
            }
        };
        final AttributeMap attributeMap = new AttributeHashMap();
        attributeMap.put(CommonKeys.AUTH_CALLBACK_HANDLER, callbackHandler);
        try {
            final Session session = new SessionWrapper(endpoint.openSession(remoteUri, attributeMap)) {
                public void close() throws RemotingException {
                    try {
                        super.close();
                    } finally {
                        endpoint.shutdown();
                    }
                }
            };
            ok = true;
            return session;
        } finally {
            if (! ok) {
                endpoint.shutdown();
            }
        }
    }

    public static <I, O> ContextSource<I, O> createEndpointAndOpenService(String endpointName, URI remoteUri, String userName, char[] password, Class<I> requestType, Class<O> replyType, String serviceType, String serviceGroupName) throws RemotingException {
        final Session session = createEndpointAndSession(endpointName, remoteUri, userName, password);
        boolean ok = false;
        try {
            final ContextSource<I, O> service = new ContextSourceWrapper<I, O>(session.openService(ServiceLocator.DEFAULT.setRequestType(requestType).setReplyType(replyType).setServiceGroupName(serviceGroupName).setServiceType(serviceType))) {
                public void close() {
                    try {
                        super.close();
                    } finally {
                        try {
                            session.close();
                        } catch (RemotingException e) {
                            log.error(e, "Failed to close Remoting session");
                        }
                    }
                }
            };
            ok = true;
            return service;
        } finally {
            if (! ok) {
                session.close();
            }
        }
    }

    // privates

    private Remoting() { /* empty */ }
}

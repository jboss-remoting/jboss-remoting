package org.jboss.cx.remoting;

import java.io.IOException;
import java.net.URI;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 *
 */
public final class EndpointLocator {
    private final URI endpointUri;
    private final String authorizationId;
    private final CallbackHandler clientCallbackHandler;

    private EndpointLocator(final URI endpointUri, final String authorizationId, final CallbackHandler clientCallbackHandler) {
        this.endpointUri = endpointUri;
        this.authorizationId = authorizationId;
        this.clientCallbackHandler = clientCallbackHandler;
    }

    public static final CallbackHandler DEFAULT_CALLBACK_HANDLER = new CallbackHandler() {
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            if (callbacks.length > 0) {
                throw new UnsupportedCallbackException(callbacks[0], "No callback types are supported");
            }
        }
    };

    public static final EndpointLocator DEFAULT = new EndpointLocator(null, null, DEFAULT_CALLBACK_HANDLER);

    public URI getEndpointUri() {
        return endpointUri;
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    public CallbackHandler getClientCallbackHandler() {
        return clientCallbackHandler;
    }

    public EndpointLocator setEndpointUri(URI endpointUri) {
        return new EndpointLocator(endpointUri, authorizationId, clientCallbackHandler);
    }

    public EndpointLocator setAuthorizationId(String authorizationId) {
        return new EndpointLocator(endpointUri, authorizationId, clientCallbackHandler);
    }

    public EndpointLocator setClientCallbackHandler(CallbackHandler clientCallbackHandler) {
        return new EndpointLocator(endpointUri, authorizationId, clientCallbackHandler);
    }

    public EndpointLocator setClientAuthentication(String userName, char[] password) {
        return new EndpointLocator(endpointUri, userName, new SimpleClientCallbackHandler(userName, password));
    }

    public static final class SimpleClientCallbackHandler implements CallbackHandler {
        private final String userName;
        private final char[] password;

        private SimpleClientCallbackHandler(final String userName, final char[] password) {
            this.userName = userName;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(userName);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback, "This handler only supports username/password callbacks");
                }
            }
        }
    }
}

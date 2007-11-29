package org.jboss.cx.remoting;

import java.net.URI;
import java.io.IOException;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

/**
 *
 */
public final class EndpointLocator {
    private final URI endpointUri;
    private final CallbackHandler clientCallbackHandler;
    private final CallbackHandler serverCallbackHandler;

    private EndpointLocator(final URI endpointUri, final CallbackHandler clientCallbackHandler, final CallbackHandler serverCallbackHandler) {
        this.endpointUri = endpointUri;
        this.clientCallbackHandler = clientCallbackHandler;
        this.serverCallbackHandler = serverCallbackHandler;
    }

    public static final CallbackHandler DEFAULT_CALLBACK_HANDLER = new CallbackHandler() {
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            if (callbacks.length > 0) {
                throw new UnsupportedCallbackException(callbacks[0], "No callback types are supported");
            }
        }
    };

    public static final EndpointLocator DEFAULT = new EndpointLocator(null, DEFAULT_CALLBACK_HANDLER, DEFAULT_CALLBACK_HANDLER);

    public URI getEndpointUri() {
        return endpointUri;
    }

    public CallbackHandler getClientCallbackHandler() {
        return clientCallbackHandler;
    }

    public CallbackHandler getServerCallbackHandler() {
        return serverCallbackHandler;
    }

    public EndpointLocator setEndpointUri(URI endpointUri) {
        return new EndpointLocator(endpointUri, clientCallbackHandler, serverCallbackHandler);
    }

    public EndpointLocator setClientCallbackHandler(CallbackHandler clientCallbackHandler) {
        return new EndpointLocator(endpointUri, clientCallbackHandler, serverCallbackHandler);
    }

    public EndpointLocator setClientAuthentication(String userName, char[] password) {
        return new EndpointLocator(endpointUri, new SimpleClientCallbackHandler(userName, password), serverCallbackHandler);
    }

    public EndpointLocator setServerCallbackHandler(CallbackHandler serverCallbackHandler) {
        return new EndpointLocator(endpointUri, clientCallbackHandler, serverCallbackHandler);
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

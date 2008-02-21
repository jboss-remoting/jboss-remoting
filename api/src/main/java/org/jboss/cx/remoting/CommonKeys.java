package org.jboss.cx.remoting;

import org.jboss.cx.remoting.core.util.AttributeKey;

import java.util.Map;
import java.util.Set;
import java.util.List;

import javax.security.auth.callback.CallbackHandler;

import static org.jboss.cx.remoting.core.util.AttributeKey.key;

/**
 * A set of attribute keys that are common across various protocols.
 */
public final class CommonKeys {
    private CommonKeys() { /* no construction */ }

    // SASL

    /**
     * A key that represents the SASL properties to be used for this connection or server.
     */
    public static final AttributeKey<Map<String, ?>> SASL_PROPERTIES = key("SASL_PROPERTIES");
    /**
     * The SASL client mechanisms to try, in order.  If none are given, then the list is generated based on the registered
     * providers.
     */
    public static final AttributeKey<List<String>> SASL_CLIENT_MECHANISMS = key("SASL_CLIENT_MECHANISMS");
    /**
     * The SASL server mechanisms to make available to clients.  If none are given, then the set is generated based on
     * the registered providers.
     */
    public static final AttributeKey<Set<String>> SASL_SERVER_MECHANISMS = key("SASL_SERVER_MECHANISMS");

    // Generic auth

    /**
     * The authentication callback handler to make available to the authentication layer.
     */
    public static final AttributeKey<CallbackHandler> AUTH_CALLBACK_HANDLER = key("AUTH_CALLBACK_HANDLER");
    /**
     * The client authorization ID to send to the server.  If not specified, defaults to the local endpoint name.  If
     * the local endpoint is anonymous, defaults to {@code null}.
     */
    public static final AttributeKey<String> AUTHORIZATION_ID = key("AUTHORIZATION_ID");

    // TODO: add keys for SSL/TLS
}

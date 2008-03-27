package org.jboss.cx.remoting;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jboss.cx.remoting.util.AttributeKey;
import static org.jboss.cx.remoting.util.AttributeKey.key;

import javax.security.auth.callback.CallbackHandler;

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
    /**
     * The maximum number of times to retry authentication before giving up and failing.
     */
    public static final AttributeKey<Integer> AUTH_MAX_RETRIES = key("AUTH_MAX_RETRIES");

    // TODO: add keys for SSL/TLS

    // Protocol keys

    /**
     * The keepalive interval.  For protocols that are represented by a connection of some sort, this property indicates
     * that a "keepalive" message should be sent at regular intervals to prevent an idle connection from being
     * automatically closed by a firewall (for example).
     */
    public static final AttributeKey<Integer> KEEPALIVE_INTERVAL = key("KEEPALIVE_INTERVAL");
}

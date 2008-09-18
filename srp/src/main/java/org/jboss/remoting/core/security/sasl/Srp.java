package org.jboss.remoting.core.security.sasl;

import java.security.Permission;
import java.security.SecurityPermission;

/**
 *
 */
public final class Srp {
    public static final String SESSION_KEY = "org.jboss.remoting.sasl.srp.sessionkey";
    public static final String VERIFIER_MODE = "org.jboss.remoting.sasl.srp.verifier";

    public static final Permission SESSION_KEY_PERMISSION = new SecurityPermission("getSessionKey");
}

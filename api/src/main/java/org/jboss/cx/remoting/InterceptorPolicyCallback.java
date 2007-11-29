package org.jboss.cx.remoting;

/**
 *
 */
public interface InterceptorPolicyCallback {
    boolean isAllowed(String interceptorName, int slot);
}

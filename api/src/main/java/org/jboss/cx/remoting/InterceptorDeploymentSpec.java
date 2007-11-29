package org.jboss.cx.remoting;

import org.jboss.cx.remoting.spi.ContextInterceptorFactory;

/**
 *
 */
public final class InterceptorDeploymentSpec {
    private final String identifier;
    private final int preference;
    private final ContextInterceptorFactory contextInterceptorFactory;

    public InterceptorDeploymentSpec(final String identifier, final int preference, final ContextInterceptorFactory contextInterceptorFactory) {
        this.identifier = identifier;
        this.preference = preference;
        this.contextInterceptorFactory = contextInterceptorFactory;
    }

    public static final InterceptorDeploymentSpec DEFAULT = new InterceptorDeploymentSpec(null, 1000, null);

    public String getIdentifier() {
        return identifier;
    }

    public ContextInterceptorFactory getContextInterceptorFactory() {
        return contextInterceptorFactory;
    }

    public InterceptorDeploymentSpec setIdentifier(String identifier) {
        return new InterceptorDeploymentSpec(identifier, preference, contextInterceptorFactory);
    }

    public InterceptorDeploymentSpec setPreference(int preference) {
        return new InterceptorDeploymentSpec(identifier, preference, contextInterceptorFactory);
    }

    public InterceptorDeploymentSpec setContextInterceptorFactory(ContextInterceptorFactory contextInterceptorFactory) {
        return new InterceptorDeploymentSpec(identifier, preference, contextInterceptorFactory);
    }
}

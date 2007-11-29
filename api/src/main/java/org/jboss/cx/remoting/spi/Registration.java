package org.jboss.cx.remoting.spi;

/**
 *
 */
public interface Registration {
    /**
     * Activate this registration by placing it into the "started" state.  In this state,
     * no reconfiguration may be done.
     */
    void start();

    /**
     * Deactivate this registration by placing it into the "stopped" state.  In this state, the
     * registration may be reconfigured.
     */
    void stop();

    /**
     * Permanently remove this registration.  Unreserves any resources associated with the registration.
     */
    void unregister();
}

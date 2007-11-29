package org.jboss.cx.remoting.spi;

/**
 *
 */
public interface Discovery {
    /**
     * Signal that the discovered route has gone offline.
     */
    void remove();

    /**
     * Change the cost of this route.
     *
     * @param cost the new cost
     */
    void updateCost(int cost);
}

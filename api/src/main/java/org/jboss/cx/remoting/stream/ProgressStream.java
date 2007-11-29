package org.jboss.cx.remoting.stream;

/**
 * A streamable object that represents the progress of a long-running operation.  The {@code update} method is called
 * periodically with informational data that can be displayed to a user.
 */
public interface ProgressStream {
    /**
     * Update progress information.
     *
     * @param operationTitle a string holding the title of the current operation
     * @param unitsDone the number of completed units of work
     * @param totalUnits the total number of units of work; -1 if unknown
     * @param approx {@code true} if the value of {@code totalUnits} is approximate (i.e. not exact)
     */
    void update(String operationTitle, int unitsDone, int totalUnits, boolean approx);
}

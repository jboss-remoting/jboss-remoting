package org.jboss.remoting;

/**
 * The version of Remoting.
 *
 * @apiviz.exclude
 */
public final class Version {

    private Version() {
    }

    /**
     * The version.
     */
    public static final String VERSION = "3.0.0.GA";

    /**
     * Print the version to {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION);
    }
}

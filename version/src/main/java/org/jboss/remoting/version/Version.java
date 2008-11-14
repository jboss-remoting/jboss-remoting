package org.jboss.remoting.version;

/**
 * The version of Remoting.
 */
public final class Version {

    private Version() {
    }

    /**
     * The version.
     */
    public static final String VERSION = "3.0.0.Beta2";

    /**
     * Print the version to {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.print(VERSION);
    }
}

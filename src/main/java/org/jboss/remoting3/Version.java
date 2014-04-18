/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

/**
 * The version of Remoting.
 *
 * @apiviz.exclude
 */
@SuppressWarnings("deprecation")
public final class Version {

    private Version() {
    }

    private static final String JAR_NAME;
    /**
     * The version string.
     *
     * @deprecated Use {@link #getVersionString()} instead.
     */
    @Deprecated
    public static final String VERSION;

    static {
        Properties versionProps = new Properties();
        String jarName = "(unknown)";
        String versionString = "(unknown)";
        try {
            versionProps.load(new InputStreamReader(Version.class.getResourceAsStream("Version.properties")));
            jarName = versionProps.getProperty("jarName", jarName);
            versionString = versionProps.getProperty("version", versionString);
        } catch (IOException ignored) {
        }
        JAR_NAME = jarName;
        VERSION = versionString;
    }

    /**
     * Get the name of the JBoss Remoting JAR.
     *
     * @return the name
     */
    public static String getJarName() {
        return JAR_NAME;
    }

    /**
     * Get the version string of JBoss Remoting.
     *
     * @return the version string
     */
    public static String getVersionString() {
        return VERSION;
    }

    /**
     * Print out the current version on {@code System.out}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.printf("JBoss Remoting version %s\n", getVersionString());
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoadException;

/**
 * Utility class to load a class loader from a Module.
 *
 * JBoss Modules is an optional dependency to JBoss Remoting. This class should only be loaded and used
 * when JBoss Modules is actually required.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2017 Red Hat inc.
 */
class ModuleLoader {

    /**
     * Return the class loader associated to the JBoss Module identified by the {@code moduleName}
     *
     * @param moduleName the name of the module (can not be {@code null}
     * @return the class loader associated to the JBoss Module
     * @throws IOException if the module can not be loaded or if JBoss Modules is not present
     */
    static ClassLoader getClassLoaderFromModule(String moduleName) throws IOException{
        try {
            return Module.getCallerModuleLoader().loadModule(moduleName).getClassLoader();
        } catch (ModuleLoadException e) {
            throw new IOException("Failed to create endpoint", e);
        } catch (LinkageError e) {
            throw new IOException("Failed to create endpoint: JBoss Modules is not present", e);
        }
    }
}

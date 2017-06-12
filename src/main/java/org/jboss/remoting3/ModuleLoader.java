/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

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

package org.jboss.remoting3.spi;

import java.io.IOException;
import org.xnio.OptionMap;

/**
 * A connection provider factory.  Implementations of this interface provide a connection facility for a URI scheme.  An
 * endpoint will call the {@code createInstance()} method with its provider context when instances of this interface
 * are registered on that endpoint.
 */
public interface ConnectionProviderFactory {

    /**
     * Create a provider instance for an endpoint.
     *
     * @param context the provider context
     * @param optionMap the options to pass to the provider factory
     * @param protocolName the name of the protocol scheme
     * @return the provider
     * @throws IOException if the provider cannot be created
     */
    ConnectionProvider createInstance(ConnectionProviderContext context, final OptionMap optionMap, final String protocolName) throws IOException;
}

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

import static org.jboss.remoting3._private.Messages.log;

import java.io.IOError;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import org.jboss.remoting3.spi.EndpointConfigurator;
import org.wildfly.client.config.ConfigXMLParseException;

/**
 * A configuration-based endpoint supplier.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConfigurationEndpointSupplier implements Supplier<Endpoint> {
    static class Holder {
        static final Endpoint CONFIGURED_ENDPOINT;

        static {
            CONFIGURED_ENDPOINT = AccessController.doPrivileged((PrivilegedAction<Endpoint>) () -> {
                Endpoint endpoint = null;
                try {
                    endpoint = RemotingXmlParser.parseEndpoint();
                } catch (ConfigXMLParseException | IOException e) {
                    log.warn("Failed to parse endpoint XML definition", e);
                }
                if (endpoint == null) {
                    final Iterator<EndpointConfigurator> iterator = ServiceLoader.load(EndpointConfigurator.class, ConfigurationEndpointSupplier.class.getClassLoader()).iterator();
                    while (endpoint == null) try {
                        if (! iterator.hasNext()) break;
                        final EndpointConfigurator configurator = iterator.next();
                        if (configurator != null) {
                            endpoint = configurator.getConfiguredEndpoint();
                        }
                    } catch (ServiceConfigurationError e) {
                        log.trace("Failed to configure a service", e);
                    }
                }
                if (endpoint == null) try {
                    endpoint = new EndpointBuilder().build();
                } catch (IOException e) {
                    throw new IOError(e);
                }
                return new UncloseableEndpoint(endpoint);
            });
        }

        private Holder() {
        }
    }

    /**
     * Construct a new instance.
     */
    public ConfigurationEndpointSupplier() {
    }

    public Endpoint get() {
        return Holder.CONFIGURED_ENDPOINT;
    }
}

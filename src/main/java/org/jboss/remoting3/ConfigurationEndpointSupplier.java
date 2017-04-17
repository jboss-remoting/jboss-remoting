/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
                    log.trace("Failed to parse endpoint XML definition", e);
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

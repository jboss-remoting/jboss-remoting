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

package org.jboss.remoting3.remote;

import java.io.IOException;

import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.xnio.OptionMap;

/**
 * A {@link org.jboss.remoting3.spi.ConnectionProviderFactory} for the {@code remote} protocol that initiates
 * the connection via a HTTP Upgrade
 *
 * @author Stuart Douglas
 */
public final class HttpUpgradeConnectionProviderFactory implements ConnectionProviderFactory {

    /**
     * Construct a new instance.
     *
     */
    public HttpUpgradeConnectionProviderFactory() {
    }

    /** {@inheritDoc} */
    public ConnectionProvider createInstance(final ConnectionProviderContext context, final OptionMap optionMap, final String protocolName) throws IOException {
        return new HttpUpgradeConnectionProvider(optionMap, context, protocolName);
    }
}

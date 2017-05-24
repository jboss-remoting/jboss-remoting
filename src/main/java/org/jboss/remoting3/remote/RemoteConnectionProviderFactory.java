/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.remoting3.remote;

import java.io.IOException;
import org.jboss.remoting3.spi.ConnectionProvider;
import org.jboss.remoting3.spi.ConnectionProviderContext;
import org.jboss.remoting3.spi.ConnectionProviderFactory;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;

/**
 * A {@link ConnectionProviderFactory} for the {@code remote} protocol.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class RemoteConnectionProviderFactory implements ConnectionProviderFactory {

    /**
     * Flag, wheter use PKCS11 keystore in jboss-remoting.
     */
    public static final Option<Boolean> JBOSS_AS_REMOTE_USEPKCS = Option.simple(Options.class, "JBOSS_AS_REMOTE_USEPKCS", Boolean.class);

    /**
     * Keystore password for the PKCS11 keystore for jboss-remoting.
     */
    public static final Option<String> JBOSS_AS_REMOTE_KEYSTOREPASSWORD = Option.simple(Options.class, "JBOSS_AS_REMOTE_KEYSTOREPASSWORD", String.class);

    /**
     * SSL protocol for the PKCS11 keystore for jboss-remoting.
     */
    public static final Option<String> JBOSS_AS_REMOTE_SSLPROTOCOL= Option.simple(Options.class, "JBOSS_AS_REMOTE_SSLPROTOCOL", String.class);

    /**
     * Construct a new instance.
     *
     */
    public RemoteConnectionProviderFactory() {
    }

    /** {@inheritDoc} */
    public ConnectionProvider createInstance(final ConnectionProviderContext context, final OptionMap optionMap) throws IOException {
        return new RemoteConnectionProvider(optionMap, context);
    }
}

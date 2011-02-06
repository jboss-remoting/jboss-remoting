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

package org.jboss.remoting3.test;

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.xnio.ChannelListener;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.SslTcpServer;
import org.xnio.Xnio;
import org.xnio.channels.ConnectedStreamChannel;
import org.testng.SkipException;
import org.testng.annotations.Test;

@Test(suiteName = "remote+ssl")
public final class RemoteSslTestCase extends AbstractRemoteTestCase {
    // Use anonymous ciphers so we don't need a trust store configuration of any sort
    private static final String[] CIPHER_SUITES = {
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",
            "SSL_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "SSL_DH_anon_WITH_DES_CBC_SHA",
            "SSL_DH_anon_WITH_RC4_128_MD5",
            "SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "SSL_DH_anon_EXPORT_WITH_RC4_40_MD5",
    };

    private static final String[] PROTOCOLS = {
            "TLSv1",
    };

    protected SslTcpServer getServer(final ChannelListener<ConnectedStreamChannel<InetSocketAddress>> listener, final Xnio xnio) throws NoSuchProviderException, NoSuchAlgorithmException {
        final OptionMap serverOptions = OptionMap.builder()
                .setSequence(Options.SSL_ENABLED_CIPHER_SUITES, CIPHER_SUITES)
                .setSequence(Options.SSL_ENABLED_PROTOCOLS, PROTOCOLS)
                .getMap();
        return xnio.createSslTcpServer(listener, serverOptions);
    }

    protected String getScheme() {
        if (false) throw new SkipException("SSL");
        return "remote+ssl";
    }
}
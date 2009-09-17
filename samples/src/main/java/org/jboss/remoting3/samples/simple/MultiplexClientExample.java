/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.samples.simple;

import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.xnio.OptionMap;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Client;
import org.jboss.xnio.IoUtils;
import java.io.IOException;
import java.net.URI;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 *
 */
public final class MultiplexClientExample {

    static {
        final Logger l = Logger.getLogger("");
        l.getHandlers()[0].setLevel(Level.ALL);
        l.setLevel(Level.INFO);
    }

    private MultiplexClientExample() {
    }

    public static void main(String[] args) {
        try {
            final Endpoint endpoint = Remoting.createEndpoint("example-client-endpoint");
            try {
                final Connection connection = endpoint.connect(URI.create(args[0]), OptionMap.EMPTY).get();
                try {
                    final Client<String,String> client = connection.openClient("samples.rot13", "*", String.class, String.class).get();
                    try {
                        final String original = "The Secret Message\n";
                        final String result = client.invoke(original);
                        System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), result.trim());
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(endpoint);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

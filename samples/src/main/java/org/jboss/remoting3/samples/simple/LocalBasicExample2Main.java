/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

import java.net.URI;
import org.jboss.remoting3.Client;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.jboss.remoting3.Connection;
import org.jboss.remoting3.Registration;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.OptionMap;

/**
 *
 */
public final class LocalBasicExample2Main {

    private LocalBasicExample2Main() {
    }

    public static void main(String[] args) throws Exception {
        final Endpoint endpoint = Remoting.getConfiguredEndpoint();
        try {
            final Registration handle = endpoint.serviceBuilder().setServiceType("simple.rot13").setGroupName("main")
                    .setRequestType(String.class).setReplyType(String.class).setClientListener(new StringRot13ClientListener())
                    .register();
            try {
                final Connection connection = endpoint.connect(new URI("local:///"), OptionMap.EMPTY).get();
                try {
                    final Client<String, String> client = connection.openClient("simple.rot13", "*", String.class, String.class).get();
                    try {
                        final String original = "The Secret Message";
                        final String result = client.invoke(original);
                        System.out.printf("The secret message \"%s\" became \"%s\"!\n", original.trim(), result.trim());
                    } finally {
                        IoUtils.safeClose(client);
                    }
                } finally {
                    IoUtils.safeClose(connection);
                }
            } finally {
                IoUtils.safeClose(handle);
            }
        } finally {
            IoUtils.safeClose(endpoint);
        }
    }
}
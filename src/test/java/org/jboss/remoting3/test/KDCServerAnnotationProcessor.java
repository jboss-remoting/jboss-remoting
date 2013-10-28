/*
 * This product includes software developed at
 * The Apache Software Foundation (http://www.apache.org/).
 *
 * Modified from work covered by the following permission notice:
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jboss.remoting3.test;

import org.apache.directory.server.annotations.CreateKdcServer;
import org.apache.directory.server.annotations.CreateTransport;
import org.apache.directory.server.core.annotations.AnnotationUtils;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.i18n.I18n;
import org.apache.directory.server.kerberos.KerberosConfig;
import org.apache.directory.server.kerberos.kdc.KdcServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;
import org.apache.directory.server.protocol.shared.transport.Transport;
import org.apache.directory.server.protocol.shared.transport.UdpTransport;
import org.apache.mina.util.AvailablePortFinder;

/**
 * Annotation processor for creating Kerberos servers - based on original implementation in
 * {@link org.apache.directory.server.factory.ServerAnnotationProcessor}. This implementation only adds a workaround for
 * https://issues.apache.org/jira/browse/DIRKRB-89<br/>
 * <p/>
 * Based on code from
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @author Josef Cacek
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KDCServerAnnotationProcessor {

    // Public methods --------------------------------------------------------

    /**
     * Creates and starts KdcServer based on configuration from {@link ExtCreateKdcServer} annotation.
     *
     * @param directoryService
     * @param startPort        start port number used for searching free ports in case the transport has no port number preconfigured.
     * @param address          if not null, use this bind address instead of the value configured in {@link Transport} annotation.
     * @return
     * @throws Exception
     */
    public static KdcServer getKdcServer(DirectoryService directoryService, int startPort, String address) throws Exception {
        final CreateKdcServer createKdcServer = (CreateKdcServer) AnnotationUtils.getInstance(CreateKdcServer.class);
        return createKdcServer(createKdcServer, directoryService, startPort, address);
    }

    // Private methods -------------------------------------------------------

    /**
     * Creates and starts {@link KdcServer} instance based on given configuration.
     *
     * @param createKdcServer
     * @param directoryService
     * @param startPort
     * @return
     */
    private static KdcServer createKdcServer(CreateKdcServer createKdcServer, DirectoryService directoryService,
                                             int startPort, String bindAddress) {
        if (createKdcServer == null) {
            return null;
        }
        KdcServer kdcServer = new KdcServer();
        kdcServer.setServiceName(createKdcServer.name());
        kdcServer.setSearchBaseDn(createKdcServer.searchBaseDn());
        KerberosConfig config = kdcServer.getConfig();
        config.setServicePrincipal(createKdcServer.kdcPrincipal());
        config.setPrimaryRealm(createKdcServer.primaryRealm());
        config.setMaximumTicketLifetime(createKdcServer.maxTicketLifetime());
        config.setMaximumRenewableLifetime(createKdcServer.maxRenewableLifetime());

        config.setPaEncTimestampRequired(false);

        CreateTransport[] transportBuilders = createKdcServer.transports();

        if (transportBuilders == null) {
            // create only UDP transport if none specified
            UdpTransport defaultTransport = new UdpTransport(AvailablePortFinder.getNextAvailable(startPort));
            kdcServer.addTransports(defaultTransport);
        } else if (transportBuilders.length > 0) {
            for (CreateTransport transportBuilder : transportBuilders) {
                String protocol = transportBuilder.protocol();
                int port = transportBuilder.port();
                int nbThreads = transportBuilder.nbThreads();
                int backlog = transportBuilder.backlog();
                final String address = bindAddress != null ? bindAddress : transportBuilder.address();

                if (port == -1) {
                    port = AvailablePortFinder.getNextAvailable(startPort);
                    startPort = port + 1;
                }

                if (protocol.equalsIgnoreCase("TCP")) {
                    Transport tcp = new TcpTransport(address, port, nbThreads, backlog);
                    kdcServer.addTransports(tcp);
                } else if (protocol.equalsIgnoreCase("UDP")) {
                    UdpTransport udp = new UdpTransport(address, port);
                    kdcServer.addTransports(udp);
                } else {
                    throw new IllegalArgumentException(I18n.err(I18n.ERR_689, protocol));
                }
            }
        }

        kdcServer.setDirectoryService(directoryService);

        // Launch the server
        try {
            kdcServer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return kdcServer;
    }

}

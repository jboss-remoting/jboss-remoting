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

import java.net.URI;

import org.wildfly.common.Assert;

/**
 * A builder for configuring a preconfigured endpoint connection.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ConnectionBuilder {
    private final URI destination;

    private int readTimeout = -1; // millis
    private int writeTimeout = -1; // millis

    private boolean setTcpKeepAlive;
    private boolean tcpKeepAlive;
    private int ipTrafficClass = -1;

    private int heartbeatInterval = -1;

    ConnectionBuilder(final URI destination) {
        this.destination = destination;
    }

    public ConnectionBuilder setReadTimeout(final int readTimeout) {
        Assert.checkMinimumParameter("readTimeout", 1L, readTimeout);
        this.readTimeout = readTimeout;
        return this;
    }

    public ConnectionBuilder setWriteTimeout(final int writeTimeout) {
        Assert.checkMinimumParameter("writeTimeout", 1L, writeTimeout);
        this.writeTimeout = writeTimeout;
        return this;
    }

    public ConnectionBuilder setTcpKeepAlive(final boolean tcpKeepAlive) {
        this.tcpKeepAlive = tcpKeepAlive;
        setTcpKeepAlive = true;
        return this;
    }

    public ConnectionBuilder setIpTrafficClass(final int ipTrafficClass) {
        this.ipTrafficClass = ipTrafficClass;
        return this;
    }

    public ConnectionBuilder setHeartbeatInterval(final int heartbeatInterval) {
        Assert.checkMinimumParameter("heartbeatInterval", 1, heartbeatInterval);
        this.heartbeatInterval = heartbeatInterval;
        return this;
    }

    URI getDestination() {
        return destination;
    }

    int getReadTimeout() {
        return readTimeout;
    }

    int getWriteTimeout() {
        return writeTimeout;
    }

    boolean isSetTcpKeepAlive() {
        return setTcpKeepAlive;
    }

    boolean isTcpKeepAlive() {
        return tcpKeepAlive;
    }

    int getIPTrafficClass() {
        return ipTrafficClass;
    }

    int getHeartbeatInterval() {
        return heartbeatInterval;
    }
}

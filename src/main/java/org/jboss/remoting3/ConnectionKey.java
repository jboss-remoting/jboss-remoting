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
import java.util.Objects;

import javax.net.ssl.SSLContext;

/**
 * A key which represents the identifying properties that always correspond to a unique connection.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class ConnectionKey {
    private final URI realUri;
    private final SSLContext sslContext;
    private final int hashCode;

    /**
     * Construct a new instance.
     *
     * @param realUri the real URI (after replacing elements via auth config and also canonicalization via the transport provider) (must not be {@code null})
     * @param sslContext the SSL context (may be null)
     */
    ConnectionKey(final URI realUri, final SSLContext sslContext) {
        this.realUri = realUri;
        this.sslContext = sslContext;
        hashCode = Objects.hash(realUri, sslContext);
    }

    URI getRealUri() {
        return realUri;
    }

    SSLContext getSslContext() {
        return sslContext;
    }

    public int hashCode() {
        return hashCode;
    }

    public boolean equals(final Object obj) {
        return obj instanceof ConnectionKey && equals((ConnectionKey) obj);
    }

    boolean equals(ConnectionKey other) {
        return this == other || other != null
            && hashCode == other.hashCode
            && realUri.equals(other.realUri)
            && Objects.equals(sslContext, other.sslContext);
    }

    public String toString() {
        return String.format("Connection key for uri=%s, ssl context=%s", realUri, sslContext);
    }
}

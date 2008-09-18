package org.jboss.remoting.core.security.sasl;

import java.nio.ByteBuffer;

import javax.security.sasl.SaslException;

/**
 *
 */
public interface NioSaslEndpoint {
    void wrap(ByteBuffer src, ByteBuffer target) throws SaslException;

    void unwrap(ByteBuffer src, ByteBuffer target) throws SaslException;

    boolean isWrappable();
}

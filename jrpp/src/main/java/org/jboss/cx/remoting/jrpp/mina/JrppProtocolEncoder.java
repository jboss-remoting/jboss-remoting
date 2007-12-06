package org.jboss.cx.remoting.jrpp.mina;

import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolEncoderOutput;
import org.apache.mina.common.IoSession;

/**
 *
 */
public final class JrppProtocolEncoder implements ProtocolEncoder {
    public void encode(IoSession session, Object message, ProtocolEncoderOutput out) throws Exception {
    }

    public void dispose(IoSession session) throws Exception {
    }
}

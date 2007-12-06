package org.jboss.cx.remoting.jrpp.mina;

import org.apache.mina.filter.codec.ProtocolCodecFactory;
import org.apache.mina.filter.codec.ProtocolEncoder;
import org.apache.mina.filter.codec.ProtocolDecoder;
import org.apache.mina.common.IoSession;

/**
 *
 */
public final class JrppCodecFactory implements ProtocolCodecFactory {
    public ProtocolEncoder getEncoder(IoSession session) throws Exception {
        return null;
    }

    public ProtocolDecoder getDecoder(IoSession session) throws Exception {
        return null;
    }
}

package org.jboss.cx.remoting.spi.stream;

/**
 *
 */
public interface RemoteStreamSerializer extends StreamSerializer {
    Object getRemoteInstance();
}

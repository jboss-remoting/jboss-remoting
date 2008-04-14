package org.jboss.cx.remoting.http;

import org.jboss.cx.remoting.spi.ByteMessageOutput;
import java.io.IOException;

/**
 *
 */
public interface OutputAction {
    void run(ByteMessageOutput target) throws IOException;
}

package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.cx.remoting.spi.marshal.IdentityResolver;
import org.jboss.xnio.log.Logger;

/**
 *
 */
public class JavaSerializationMarhsaller extends AbstractSerializationMarshaller {

    private static final Logger log = Logger.getLogger(JBossSerializationMarhsaller.class);

    public JavaSerializationMarhsaller(final Executor executor, final ObjectResolver resolver) throws IOException {
        super(executor, resolver);
    }

    protected ObjectOutputStream getObjectOutputStream() throws IOException {
        return new OurObjectOutputStream(outputStream, resolver == null ? IdentityResolver.getInstance() : resolver);
    }

    private static final class OurObjectOutputStream extends ObjectOutputStream {
        private final ObjectResolver resolver;

        private OurObjectOutputStream(final OutputStream outputStream, final ObjectResolver resolver) throws IOException {
            super(outputStream);
            enableReplaceObject(true);
            this.resolver = resolver;
        }

        protected Object replaceObject(final Object obj) throws IOException {
            return resolver.writeReplace(obj);
        }

        protected void writeStreamHeader() throws IOException {
            // no headers
        }
    }
}
package org.jboss.cx.remoting.core.marshal;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;
import org.jboss.cx.remoting.spi.marshal.Marshaller;
import org.jboss.cx.remoting.spi.marshal.ObjectResolver;
import org.jboss.serial.io.JBossObjectOutputStream;

/**
 *
 */
public class JBossSerializationMarhsaller implements Marshaller<ByteBuffer> {

    private final Executor executor;

    private final OurObjectOutputStream objectOutputStream;
    private final OneBufferOutputStream outputStream;

    private final Object resultLock = new Object();
    private boolean done = false;

    public JBossSerializationMarhsaller(final Executor executor, final ObjectResolver resolver) throws IOException {
        this.executor = executor;
        outputStream = new OneBufferOutputStream(new Object());
        objectOutputStream = new OurObjectOutputStream(outputStream, resolver);
    }

    public void start(final Object object) throws IOException, IllegalStateException {
        executor.execute(new Runnable() {
            public void run() {
                try {
                    synchronized (objectOutputStream) {
                        objectOutputStream.writeObject(object);
                        objectOutputStream.flush();
                        synchronized (resultLock) {
                            outputStream.flush();
                            done = true;
                            resultLock.notify();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public boolean marshal(final ByteBuffer buffer) throws IOException {
        outputStream.setBuffer(buffer);
        synchronized (resultLock) {
            outputStream.await();
            return done;
        }
    }

    public void clearClassPool() throws IOException {
        synchronized (objectOutputStream) {
            objectOutputStream.reset();
        }
    }

    private static final class OurObjectOutputStream extends JBossObjectOutputStream {
        private final ObjectResolver resolver;

        private OurObjectOutputStream(final OutputStream outputStream, final ObjectResolver resolver) throws IOException {
            super(outputStream);
            enableReplaceObject(true);
            this.resolver = resolver;
        }

        protected Object replaceObject(final Object obj) throws IOException {
            return resolver.writeReplace(obj);
        }
    }
}

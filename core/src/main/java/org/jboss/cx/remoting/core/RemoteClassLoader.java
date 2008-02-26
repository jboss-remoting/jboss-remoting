package org.jboss.cx.remoting.core;

import java.io.IOException;
import java.io.InputStream;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.service.ClassLoaderResourceRequest;
import org.jboss.cx.remoting.service.ClassLoaderResourceReply;
import org.jboss.cx.remoting.service.RemoteResource;
import org.jboss.cx.remoting.stream.ObjectSource;

/**
 *
 */
public final class RemoteClassLoader extends ClassLoader {
    private static final Logger log = Logger.getLogger(RemoteClassLoader.class);

    private final Context<ClassLoaderResourceRequest, ClassLoaderResourceReply> loaderContext;

    public RemoteClassLoader(ClassLoader parent, final Context<ClassLoaderResourceRequest, ClassLoaderResourceReply> loaderContext) {
        super(parent);
        this.loaderContext = loaderContext;
    }

    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            final ClassLoaderResourceReply reply = loaderContext.invoke(new ClassLoaderResourceRequest(name + ".class"));
            final ObjectSource<RemoteResource> source = reply.getResources();
            try {
                if (! source.hasNext()) {
                    throw new ClassNotFoundException("No resources matched");
                }
                final RemoteResource resource = source.next();
                final InputStream stream = resource.getInputStream();
                try {
                    final int size = resource.getSize();
                    final byte[] bytes = new byte[size];
                    for (int t = 0; t < size; t += stream.read(bytes, t, size - t));
                    return defineClass(name, bytes, 0, size);
                } finally {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        log.trace("Stream close failed", e);
                    }
                }
            } finally {
                try {
                    source.close();
                } catch (IOException e) {
                    log.trace("Resource ObjectSource close failed", e);
                }
            }
        } catch (RemotingException e) {
            throw new ClassNotFoundException("Cannot load class " + name + " due to an invocation failure", e);
        } catch (RemoteExecutionException e) {
            throw new ClassNotFoundException("Cannot load class " + name + " due to a remote invocation failure", e.getCause());
        } catch (IOException e) {
            throw new ClassNotFoundException("Cannot load class " + name + " due to an input/output error", e);
        }
    }
}

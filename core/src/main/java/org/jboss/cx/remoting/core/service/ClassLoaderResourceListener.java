package org.jboss.cx.remoting.core.service;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import org.jboss.cx.remoting.AbstractRequestListener;
import org.jboss.cx.remoting.ContextContext;
import org.jboss.cx.remoting.IOExceptionCarrier;
import org.jboss.cx.remoting.RemoteExecutionException;
import org.jboss.cx.remoting.RequestContext;
import org.jboss.cx.remoting.ServiceContext;
import org.jboss.cx.remoting.service.ClassLoaderResourceReply;
import org.jboss.cx.remoting.service.ClassLoaderResourceRequest;
import org.jboss.cx.remoting.service.RemoteResource;
import org.jboss.cx.remoting.stream.ObjectSource;
import org.jboss.cx.remoting.stream.ObjectSourceWrapper;
import org.jboss.cx.remoting.stream.Streams;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.util.Translator;

/**
 *
 */
public final class ClassLoaderResourceListener extends AbstractRequestListener<ClassLoaderResourceRequest,ClassLoaderResourceReply> {
    private ClassLoader classLoader;

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void setClassLoader(final ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void handleRequest(final RequestContext<ClassLoaderResourceReply> requestContext, final ClassLoaderResourceRequest request) throws RemoteExecutionException, InterruptedException {
        try {
            final Enumeration<URL> urlResources = classLoader.getResources(request.getName());
            final Enumeration<RemoteResource> actualResources = CollectionUtil.translate(urlResources, new Translator<URL, RemoteResource>() {
                public RemoteResource translate(final URL input) {
                    try {
                        final RemoteResource resource = new RemoteResource();
                        final URLConnection urlConnection = input.openConnection();
                        final int size = urlConnection.getContentLength();
                        resource.setInputStream(urlConnection.getInputStream());
                        resource.setSize(size);
                        return resource;
                    } catch (IOException ex) {
                        throw new IOExceptionCarrier(ex);
                    }
                }
            });
            final ObjectSource<RemoteResource> resourceSequence = new ObjectSourceWrapper<RemoteResource>(Streams.getEnumerationObjectSource(actualResources)) {
                public RemoteResource next() throws IOException {
                    try {
                        return super.next();
                    } catch (IOExceptionCarrier ex) {
                        throw ex.getCause();
                    }
                }
            };
            ClassLoaderResourceReply reply = new ClassLoaderResourceReply();
            reply.setResources(resourceSequence);
            requestContext.sendReply(reply);
        } catch (IOException e) {
            throw new RemoteExecutionException("Unable to get resources: " + e.getMessage(), e);
        }
    }
}

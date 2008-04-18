package org.jboss.cx.remoting.util;

import java.util.concurrent.ThreadFactory;

/**
 * A wrapper {@code ThreadFactory} that gives the threads a sensible name.
 */
public final class NamingThreadFactory implements ThreadFactory {
    private final ThreadFactory delegate;
    private final String namePattern;

    /**
     * Create a new instance.  The pattern is a format string such as one would provide to
     * {@link String#format(String, Object[])}.  The format string should contain one {@code %s} which will be replaced
     * with the original thread name.
     *
     * @param delegate the thread factory to actually use to produce the thread
     * @param namePattern the name pattern to apply
     */
    public NamingThreadFactory(final ThreadFactory delegate, final String namePattern) {
        this.delegate = delegate;
        this.namePattern = namePattern;
    }

    public Thread newThread(final Runnable r) {
        final Thread thread = delegate.newThread(r);
        final String originalName = thread.getName();
        try {
            thread.setName(String.format(namePattern, originalName));
        } catch (SecurityException ex) {
            // oh well, we tried
        }
        return thread;
    }
}

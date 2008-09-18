/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting.core.util;

import java.util.concurrent.Executor;
import java.util.Set;
import org.jboss.remoting.util.CollectionUtil;

/**
 *
 */
public final class TaggingExecutor implements Executor {

    private final Set<Task> tasks = CollectionUtil.synchronizedHashSet();
    private final Executor executor;

    public TaggingExecutor(final Executor executor) {
        this.executor = executor;
    }

    private final class Task implements Runnable {
        private volatile Thread thread;
        private final Runnable runnable;

        private Task(final Runnable runnable) {
            this.runnable = runnable;
        }

        public void run() {
            thread = Thread.currentThread();
            tasks.add(this);
            try {
                runnable.run();
            } finally {
                tasks.remove(this);
                thread = null;
            }
        }
    }

    public void execute(final Runnable command) {
        executor.execute(new Task(command));
    }

    public void interruptAll() {
        synchronized (tasks) {
            for (Task task : tasks) {
                final Thread thread = task.thread;
                if (thread != null) {
                    thread.interrupt();
                }
            }
        }
    }
}

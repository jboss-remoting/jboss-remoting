/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.remote;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.NoSuchElementException;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.remoting3.stream.ObjectSource;

final class UnmarshallerObjectSource<T> implements ObjectSource<T> {
    private final Unmarshaller unmarshaller;
    private State state;

    enum State {
        NEW,
        READY,
        DONE,
    }

    UnmarshallerObjectSource(final Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    public boolean hasNext() throws IOException {
        synchronized (this) {
            if (state == State.NEW) {
                final int cmd = unmarshaller.readUnsignedByte();
                if (cmd == RemoteProtocol.OSOURCE_OBJECT) {
                    state = State.READY;
                } else {
                    state = State.DONE;
                    unmarshaller.close();
                    return false;
                }
            }
            return state == State.READY;
        }
    }

    @SuppressWarnings({ "unchecked" })
    public T next() throws NoSuchElementException, IOException {
        synchronized (this) {
            if (hasNext()) {
                try {
                    final T obj = (T) unmarshaller.readObject();
                    state = State.NEW;
                    return obj;
                } catch (ClassNotFoundException e) {
                    state = State.NEW;
                    throw new InvalidObjectException("Class not found: " + e);
                }
            } else {
                throw new NoSuchElementException();
            }
        }
    }

    public void close() throws IOException {
        synchronized (this) {
            state = State.DONE;
            unmarshaller.close();
        }
    }
}

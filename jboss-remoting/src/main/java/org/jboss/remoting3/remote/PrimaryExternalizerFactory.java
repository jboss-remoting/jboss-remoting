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
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.jboss.marshalling.ClassExternalizerFactory;
import org.jboss.marshalling.Creator;
import org.jboss.marshalling.Externalizer;

final class PrimaryExternalizerFactory implements ClassExternalizerFactory {

    static final ClassExternalizerFactory INSTANCE = new PrimaryExternalizerFactory();

    public Externalizer getExternalizer(final Class<?> type) {
        if (type == UnsentRequestHandlerConnector.class) {
            return new RequestHandlerConnectorExternalizer();
        }
        return null;
    }

    static class RequestHandlerConnectorExternalizer implements Externalizer {
        static final RequestHandlerConnectorExternalizer INSTANCE = new RequestHandlerConnectorExternalizer();

        private static final long serialVersionUID = 8137262079765758375L;

        public void writeExternal(final Object subject, final ObjectOutput output) throws IOException {
            final UnsentRequestHandlerConnector connector = (UnsentRequestHandlerConnector) subject;
            output.writeInt(connector.getClientId());
        }

        public Object createExternal(final Class<?> subjectType, final ObjectInput input, final Creator defaultCreator) throws IOException, ClassNotFoundException {
            return new ReceivedRequestHandlerConnector(RemoteConnectionHandler.getCurrent(), input.readInt());
        }

        public void readExternal(final Object subject, final ObjectInput input) throws IOException, ClassNotFoundException {
            // n/a
        }
    }
}

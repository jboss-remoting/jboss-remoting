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

package org.jboss.cx.remoting.protocol.basic;

/**
 *
 */
public final class MessageType {
    //
    public static final int REQUEST_ONEWAY     = 0;
    public static final int REQUEST            = 1;
    public static final int REPLY              = 2;
    public static final int CANCEL_REQUEST     = 3;
    public static final int CANCEL_ACK         = 4;
    public static final int REQUEST_FAILED     = 5;
    // Remote side called .close() on a forwarded RemoteClientEndpoint
    public static final int CLIENT_CLOSE       = 6;
    // Remote side called .close() on a forwarded RemoteClientEndpoint
    public static final int CLIENT_OPEN        = 7;
    public static final int SERVICE_CLOSE      = 8;

    private MessageType() {
    }
}

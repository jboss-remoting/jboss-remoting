/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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

package org.jboss.remoting3;

/**
 * A pair of interconnected channels.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ChannelPair {
    private final Channel leftChannel;
    private final Channel rightChannel;

    /**
     * Construct a new instance.
     *
     * @param leftChannel the left-hand channel
     * @param rightChannel the right-hand channel
     */
    public ChannelPair(final Channel leftChannel, final Channel rightChannel) {
        this.leftChannel = leftChannel;
        this.rightChannel = rightChannel;
    }

    /**
     * Get the left-hand channel.
     *
     * @return the left-hand channel
     */
    public Channel getLeftChannel() {
        return leftChannel;
    }

    /**
     * Get the right-hand channel.
     *
     * @return the right-hand channel
     */
    public Channel getRightChannel() {
        return rightChannel;
    }
}

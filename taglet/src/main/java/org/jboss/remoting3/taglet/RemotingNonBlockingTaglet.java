/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.taglet;

import com.sun.javadoc.Tag;

public final class RemotingNonBlockingTaglet extends RemotingTypeTaglet {

    public String getName() {
        return "remoting.nonblocking";
    }

    public String toString(final Tag tag) {
        return "<p><b>Non-blocking method</b> - this method is expected to operate on a non-blocking basis.  That is, " +
                "the method is expected to return in a relatively short amount of time, and the result of the method is " +
                "not expected to be available at the time this method returns.  Instead, the " +
                "method implementation should take whatever steps are necessary to initiate the operation asynchronously " +
                "and then return.  If a result is available immediately, it is allowed to report the result immediately.\n";
    }
}
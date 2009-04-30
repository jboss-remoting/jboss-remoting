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

package org.jboss.remoting3;

import org.jboss.xnio.AbstractIoFuture;
import org.jboss.remoting3.spi.Result;
import java.io.IOException;

abstract class FutureResult<T, X> extends AbstractIoFuture<T> {
    private final Result<X> result = new Result<X>() {
        public void setResult(final X result) {
            try {
                FutureResult.this.setResult(translate(result));
            } catch (IOException e) {
                FutureResult.this.setException(e);
            }
        }

        public void setException(final IOException exception) {
            FutureResult.this.setException(exception);
        }

        public void setCancelled() {
            finishCancel();
        }
    };

    abstract protected T translate(X result) throws IOException;

    Result<X> getResult() {
        return result;
    }

    protected boolean setException(final IOException exception) {
        return super.setException(exception);
    }

    protected boolean setResult(final T result) {
        return super.setResult(result);
    }

    protected boolean finishCancel() {
        return super.finishCancel();
    }
}

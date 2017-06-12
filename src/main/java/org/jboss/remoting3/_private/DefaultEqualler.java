/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3._private;

import java.io.Serializable;

final class DefaultEqualler implements Equaller<Object>, Serializable {

    private static final long serialVersionUID = -5237758393814640207L;

    DefaultEqualler() {
    }

    public boolean equals(final Object obj, final Object other) {
        return obj == null ? other == null : obj.equals(other);
    }

    protected Object readResolve() {
        return Equaller.DEFAULT;
    }
}

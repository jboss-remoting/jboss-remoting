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

/**
 * An equals-comparator.
 *
 * @param <T> the type to compare
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface Equaller<T> {

    /**
     * Test the two objects for equality.
     *
     * @param obj the object to compare
     * @param other the other object
     * @return {@code true} if they are equivalent, {@code false} otherwise
     */
    boolean equals(T obj, T other);

    Equaller<Object> IDENTITY = new IdentityEqualler();

    Equaller<Object> DEFAULT = new DefaultEqualler();
}


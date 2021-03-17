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

package org.jboss.remoting3.spi;

import org.jboss.remoting3.Connection;
import org.jboss.remoting3.OpenListener;
import org.xnio.OptionMap;

/**
 * A registered service.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface RegisteredService {

    /**
     * Get the service open listener.
     *
     * @return the service open listener
     */
    OpenListener getOpenListener();

    /**
     * Get the service option map.
     *
     * @return the service option map
     */
    OptionMap getOptionMap();

    /**
     * Validate the service for the given connection.
     *
     * @param connection the connection (must not be {@code null})
     * @return {@code true} if the service is allowed, {@code false} otherwise
     */
    default boolean validateService(Connection connection) {
        return true;
    }
}

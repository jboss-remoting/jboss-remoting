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

package org.jboss.remoting3;

import org.xnio.OptionMap;

/**
 * A listener for new channels.  An open listener is registered with an {@code Endpoint}
 * via the {@link Endpoint#registerService(String, OpenListener, OptionMap)} method.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface OpenListener {

    /**
     * Notification that a new channel has been opened for this service.
     *
     * @param channel the channel that was opened
     */
    void channelOpened(Channel channel);

    /**
     * Called when this registration has been terminated.
     */
    void registrationTerminated();
}

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

package org.jboss.remoting3.test;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jboss.remoting3.ChannelPair;
import org.jboss.remoting3.Endpoint;
import org.jboss.remoting3.Remoting;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.xnio.IoUtils;
import org.xnio.OptionMap;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
@Test
public final class LocalChannelTest extends ChannelTestBase {
    protected Endpoint endpoint;
    protected ExecutorService executorService;

    @BeforeClass
    public void create() throws IOException {
        executorService = new ThreadPoolExecutor(16, 16, 1L, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>());
        endpoint = Remoting.createEndpoint("test", executorService, OptionMap.EMPTY);
    }

    @BeforeMethod
    public void testStart() {
        ChannelPair channelPair = endpoint.createChannelPair();
        sendChannel = channelPair.getLeftChannel();
        recvChannel = channelPair.getRightChannel();
    }

    @AfterMethod
    public void testFinish() {
        IoUtils.safeClose(sendChannel);
        IoUtils.safeClose(recvChannel);
    }

    @AfterClass
    public void destroy() throws IOException, InterruptedException {
        endpoint.close();
        executorService.shutdown();
        executorService.awaitTermination(1L, TimeUnit.DAYS);
        executorService.shutdownNow();
    }
}

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

/**
 * The base Remoting 3 API package.
 * <p/>
 * The main flow of client requests works like this:
 * <p/>
 * {@websequence.show mainflow}
 * <p/>
 *
 * @apiviz.exclude org.jboss.remoting.transporter
 *
 * @websequence.diagram mainflow
 * participant "User code" as user
 * participant "IoFuture<Reply>" as fr
 * participant "Client\n(endpoint A)" as client
 * participant "Protocol's\nRequest Handler" as rh1
 * participant "~~~\n~~~"
 * participant "Local Request\nHandler (endpoint B)" as rh2
 * participant "RequestListener\n(endpoint B)" as rl
 * loop
 * user->client: "client.send(request);"
 * activate client
 * client->rh1: "requestHandler\n.receiveRequest();"
 * activate rh1
 * client->fr: "...creates..."
 * activate fr
 * deactivate client
 * fr->user: "return\nfutureReply;"
 * deactivate fr
 * activate user
 * rh1-->rh2: Marshalled request
 * deactivate rh1
 * activate rh2
 * rh2->rl: listener\n.handleRequest()
 * deactivate rh2
 * activate rl
 * rl->rh2: context\n.sendReply()
 * activate rh2
 * deactivate rl
 * rh2-->rh1: Marshalled reply
 * deactivate rh2
 * activate rh1
 * rh1->fr: replyHandler.handleReply()
 * deactivate rh1
 * activate fr
 * fr->user: invoke notifiers\n(async)
 * deactivate fr
 * user->fr: futureReply.get();
 * deactivate user
 * activate fr
 * fr->user: reply
 * destroy fr
 * activate user
 * end
 */
package org.jboss.remoting3;
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
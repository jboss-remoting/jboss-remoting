/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting.protocol.multiplex;

import org.jboss.xnio.IoReadHandler;
import org.jboss.xnio.IoUtils;
import org.jboss.xnio.IoFuture;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.log.Logger;
import org.jboss.xnio.channels.AllocatedMessageChannel;
import org.jboss.remoting.spi.RequestHandler;
import org.jboss.remoting.spi.Handle;
import org.jboss.remoting.spi.ReplyHandler;
import org.jboss.remoting.spi.SpiUtils;
import org.jboss.remoting.spi.RemoteRequestContext;
import org.jboss.remoting.spi.RequestHandlerSource;
import org.jboss.remoting.ReplyException;
import org.jboss.remoting.RemoteExecutionException;
import org.jboss.remoting.ServiceRegistrationException;
import org.jboss.remoting.util.QualifiedName;
import org.jboss.marshalling.Unmarshaller;
import org.jboss.marshalling.Marshalling;
import org.jboss.marshalling.Marshaller;
import org.jboss.marshalling.MarshallerFactory;
import org.jboss.marshalling.MarshallingConfiguration;
import java.nio.ByteBuffer;
import java.nio.BufferUnderflowException;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

/**
 *
 */
public final class MultiplexReadHandler implements IoReadHandler<AllocatedMessageChannel> {

    private static final Logger log = Logger.getLogger("org.jboss.remoting.multiplex");
    private static final StackTraceElement[] emptyStackTraceElements = new StackTraceElement[0];
    private final MultiplexConnection connection;

    public MultiplexReadHandler(final MultiplexConnection connection) {
        this.connection = connection;
    }

    public void handleReadable(final AllocatedMessageChannel channel) {
        final MultiplexConnection connection = this.connection;
        final MarshallerFactory marshallerFactory = connection.getMarshallerFactory();
        final MarshallingConfiguration marshallingConfiguration = connection.getMarshallingConfiguration();
        for (;;) try {
            final ByteBuffer buffer;
            try {
                buffer = channel.receive();
            } catch (IOException e) {
                log.error(e, "I/O error in protocol channel; closing channel");
                IoUtils.safeClose(channel);
                return;
            }
            if (buffer == null) {
                IoUtils.safeClose(channel);
                return;
            }
            if (! buffer.hasRemaining()) {
                // would block
                channel.resumeReads();
                return;
            }
            final MessageType msgType;
            try {
                msgType = MessageType.getMessageType(buffer.get() & 0xff);
            } catch (IllegalArgumentException ex) {
                log.trace("Received invalid message type");
                return;
            }
            log.trace("Received message type %s; dump:\n%s", msgType, Buffers.createDumper(buffer, 8, 1));
            switch (msgType) {
                case REQUEST: {
                    final int clientId = buffer.getInt();
                    final Handle<RequestHandler> handle = connection.getForwardedClient(clientId);
                    if (handle == null) {
                        log.trace("Request on invalid client ID %d", Integer.valueOf(clientId));
                        break;
                    }
                    final int requestId = buffer.getInt();
                    final Object payload;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            payload = unmarshaller.readObject();
                            unmarshaller.finish();
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (Exception ex) {
                        // IOException | ClassNotFoundException
                        log.trace("Failed to unmarshal a request (%s), sending %s", ex, MessageType.REQUEST_RECEIVE_FAILED);
                        try {
                            final Marshaller marshaller = marshallerFactory.createMarshaller(marshallingConfiguration);
                            try {
                                List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();
                                marshaller.start(new BufferByteOutput(connection.getAllocator(), buffers));
                                marshaller.write(MessageType.REQUEST_RECEIVE_FAILED.getId());
                                ex.setStackTrace(emptyStackTraceElements);
                                final IOException ioe = new IOException("Request receive failed");
                                ioe.initCause(ex);
                                ioe.setStackTrace(emptyStackTraceElements);
                                marshaller.writeObject(ioe);
                                marshaller.finish();
                                connection.doBlockingWrite(buffers);
                            } finally {
                                IoUtils.safeClose(marshaller);
                            }
                        } catch (IOException ioe) {
                            log.warn("Failed to send notification of failure to unmarshal a request: %s", ioe);
                        }
                        break;
                    }
                    // request received OK
                    final RequestHandler requestHandler = handle.getResource();
                    requestHandler.receiveRequest(payload, new MultiplexReplyHandler(requestId, connection));
                    break;
                }
                case REPLY: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = connection.removeRemoteRequest(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final Object payload;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            payload = unmarshaller.readObject();
                            unmarshaller.finish();
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (Exception ex) {
                        // IOException | ClassNotFoundException
                        log.trace("Failed to unmarshal a reply (%s), sending a ReplyException", ex);
                        SpiUtils.safeHandleException(replyHandler, new ReplyException("Unmarshal failed", ex));
                        break;
                    }
                    SpiUtils.safeHandleReply(replyHandler, payload);
                    break;
                }
                case CANCEL_REQUEST: {
                    final int requestId = buffer.getInt();
                    final RemoteRequestContext context = connection.getLocalRequest(requestId);
                    if (context != null) {
                        context.cancel();
                    }
                    break;
                }
                case CANCEL_ACK: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = connection.getRemoteRequest(requestId);
                    if (replyHandler != null) {
                        SpiUtils.safeHandleCancellation(replyHandler);
                    }
                    break;
                }
                case REQUEST_RECEIVE_FAILED: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = connection.removeRemoteRequest(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final IOException cause;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            cause = (IOException) unmarshaller.readObject();
                            unmarshaller.finish();
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (IOException e) {
                        SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote operation failed; the remote exception could not be read", e));
                        break;
                    } catch (ClassNotFoundException e) {
                        SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote operation failed; the remote exception could not be read", e));
                        break;
                    }
                    SpiUtils.safeHandleException(replyHandler, cause);
                    break;
                }
                case REQUEST_FAILED: {
                    final int requestId = buffer.getInt();
                    final ReplyHandler replyHandler = connection.removeRemoteRequest(requestId);
                    if (replyHandler == null) {
                        log.trace("Got reply to unknown request %d", Integer.valueOf(requestId));
                        break;
                    }
                    final IOException cause;
                    try {
                        final Unmarshaller unmarshaller = marshallerFactory.createUnmarshaller(marshallingConfiguration);
                        try {
                            unmarshaller.start(Marshalling.createByteInput(buffer));
                            try {
                                cause = (IOException) unmarshaller.readObject();
                            } catch (ClassNotFoundException e) {
                                SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote request failed (and an ClassNotFoundException occurred when attempting to unmarshal the cause)"));
                                log.trace(e, "Class not found in exception reply to request ID %d", Integer.valueOf(requestId));
                                break;
                            } catch (ClassCastException e) {
                                SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote request failed (and an ClassCastException occurred when attempting to unmarshal the cause)"));
                                log.trace(e, "Class cast exception in exception reply to request ID %d", Integer.valueOf(requestId));
                                break;
                            }
                        } finally {
                            IoUtils.safeClose(unmarshaller);
                        }
                    } catch (IOException ex) {
                        log.trace("Failed to unmarshal an exception reply (%s), sending a generic execution exception");
                        SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote request failed (and an unexpected I/O error occurred when attempting to read the cause)"));
                        break;
                    }
                    SpiUtils.safeHandleException(replyHandler, new RemoteExecutionException("Remote execution failed", cause));
                    break;
                }
                case CLIENT_CLOSE: {
                    final int clientId = buffer.getInt();
                    final Handle<RequestHandler> handle = connection.removeForwardedClient(clientId);
                    if (handle == null) {
                        log.warn("Got client close message for unknown client %d", Integer.valueOf(clientId));
                        break;
                    }
                    IoUtils.safeClose(handle);
                    break;
                }
                case CLIENT_OPEN: {
                    final int serviceId = buffer.getInt();
                    final int clientId = buffer.getInt();
                    final Handle<RequestHandlerSource> handle = connection.getForwardedService(serviceId);
                    if (handle == null) {
                        log.warn("Received client open message for unknown service %d", Integer.valueOf(serviceId));
                        break;
                    }
                    try {
                        final RequestHandlerSource requestHandlerSource = handle.getResource();
                        final Handle<RequestHandler> clientHandle = requestHandlerSource.createRequestHandler();
                        log.trace("Opening client %d from service %d", Integer.valueOf(clientId), Integer.valueOf(serviceId));
                        connection.addForwardedClient(clientId, clientHandle);
                    } catch (IOException ex) {
                        log.error(ex, "Failed to create a request handler for client ID %d", Integer.valueOf(clientId));
                        break;
                    } finally {
                        IoUtils.safeClose(handle);
                    }
                    break;
                }
                case SERVICE_OPEN_REQUEST: {
                    final int serviceId = buffer.getInt();
                    final QualifiedName qualifiedName = MultiplexConnection.getQualifiedName(buffer);
                    final Handle<RequestHandlerSource> service = connection.getService(qualifiedName);
                    if (service == null) {
                        ByteBuffer replyBuffer = ByteBuffer.allocate(5);
                        replyBuffer.put((byte) MessageType.SERVICE_OPEN_NOT_FOUND.getId());
                        replyBuffer.putInt(serviceId);
                        replyBuffer.flip();
                        try {
                            connection.doBlockingWrite(replyBuffer);
                        } catch (IOException e) {
                            log.error(e, "Failed to send an error reply to an invalid service open request");
                        }
                        break;
                    }
                    final Handle<RequestHandlerSource> ourHandle;
                    try {
                        ourHandle = service.getResource().getHandle();
                    } catch (IOException e) {
                        log.error("Failed to acquire a handle to registered service: %s", e);
                        ByteBuffer replyBuffer = ByteBuffer.allocate(5);
                        replyBuffer.put((byte) MessageType.SERVICE_OPEN_FAILED.getId());
                        replyBuffer.putInt(serviceId);
                        replyBuffer.flip();
                        try {
                            connection.doBlockingWrite(replyBuffer);
                        } catch (IOException e2) {
                            log.trace(e, "Failed to send an exception reply to a service open request");
                        }
                        break;
                    }
                    connection.addForwadedService(serviceId, ourHandle);
                    ByteBuffer replyBuffer = ByteBuffer.allocate(5);
                    replyBuffer.put((byte) MessageType.SERVICE_OPEN_REPLY.getId());
                    replyBuffer.putInt(serviceId);
                    replyBuffer.flip();
                    try {
                        connection.doBlockingWrite(replyBuffer);
                    } catch (IOException e) {
                        log.trace(e, "Failed to send a reply to a service open request");
                    }
                    break;
                }
                case SERVICE_OPEN_FAILED:
                case SERVICE_OPEN_NOT_FOUND:
                case SERVICE_OPEN_FORBIDDEN: {
                    final int serviceId = buffer.getInt();
                    final FutureRemoteRequestHandlerSource future = connection.removeFutureRemoteService(serviceId);
                    if (future == null) {
                        log.trace("Service open failure reply received for unknown service ID %d", Integer.valueOf(serviceId));
                        break;
                    }
                    future.setException(
                            msgType == MessageType.SERVICE_OPEN_NOT_FOUND ? new ServiceRegistrationException("Service not found") :
                            msgType == MessageType.SERVICE_OPEN_FORBIDDEN ? new ServiceRegistrationException("Service open forbidden") :
                            new ServiceRegistrationException("Service open failed")
                    );
                    break;
                }
                case SERVICE_OPEN_REPLY: {
                    final int serviceId = buffer.getInt();
                    final FutureRemoteRequestHandlerSource future = connection.getFutureRemoteService(serviceId);
                    if (future == null) {
                        log.trace("Service open reply received for unknown service ID %d", Integer.valueOf(serviceId));
                        break;
                    }
                    final MultiplexRequestHandlerSource requestHandlerSource = new MultiplexRequestHandlerSource(serviceId, connection);
                    future.setResult(requestHandlerSource);
                    break;
                }
                case SERVICE_CLOSE_NOTIFY: {
                    final int serviceId = buffer.getInt();
                    final FutureRemoteRequestHandlerSource future = connection.removeFutureRemoteService(serviceId);
                    future.addNotifier(new IoFuture.HandlingNotifier<RequestHandlerSource>() {
                        public void handleDone(final RequestHandlerSource result) {
                            IoUtils.safeClose(result);
                        }
                    });
                    break;
                }
                case SERVICE_CLOSE_REQUEST: {
                    final int serviceId = buffer.getInt();
                    final Handle<RequestHandlerSource> handle = connection.removeForwardedService(serviceId);
                    if (handle == null) {
                        log.trace("Received service close request on unknown ID %d", Integer.valueOf(serviceId));
                        break;
                    }
                    IoUtils.safeClose(handle);
                    break;
                }
                default: {
                    log.error("Malformed packet received (invalid message type %s)", msgType);
                }
                case CONNECTION_CLOSE:
                    break;
            }
        } catch (BufferUnderflowException e) {
            log.error(e, "Malformed packet received (buffer underflow)");
        }
    }
}

package org.jboss.cx.remoting.core;

import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.util.AtomicStateMachine;
import org.jboss.cx.remoting.log.Logger;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;

/**
 *
 */
public final class CoreOutboundService<I, O> {
    private static final Logger log = Logger.getLogger(CoreOutboundService.class);

    private final ServiceIdentifier serviceIdentifier;
    private CoreSession coreSession;
    private final AtomicStateMachine<State> state = AtomicStateMachine.start(State.WAITING_FOR_REPLY);
    private final ContextSource<I, O> userContextSource = new UserContextSource();

    private enum State {
        WAITING_FOR_REPLY,
        UP,
        FAILED,
        DOWN
    }

    protected CoreOutboundService(final CoreSession coreSession, final ServiceIdentifier serviceIdentifier) {
        this.coreSession = coreSession;
        this.serviceIdentifier = serviceIdentifier;
    }

    // State mgmt

    void await() throws RemotingException {
        if (state.waitForNot(State.WAITING_FOR_REPLY) == State.FAILED) {
            throw new RemotingException("Failed to open service");
        }
    }

    // Outbound protocol messages

    void sendServiceRequest() throws RemotingException {
    }

    // Inbound protocol messages

    void receiveServiceActivate() {
        if (! state.transition(State.WAITING_FOR_REPLY, State.UP)) {
            log.trace("Received unsolicited service activation for service (%s)", serviceIdentifier);
        }
    }

    void receiveServiceTerminate() {
        if (state.transition(State.UP, State.DOWN) || state.transition(State.WAITING_FOR_REPLY, State.FAILED)) {
            closeService();
        }
    }

    // Other protocol-related

    void closeService() {
        try {
            coreSession.closeService(serviceIdentifier);
        } catch (RemotingException e) {
            log.trace("Failed to close service (%s): %s", serviceIdentifier, e.getMessage());
        }
    }

    // Getters

    ContextSource<I, O> getUserContextSource() {
        return userContextSource;
    }

    public final class UserContextSource implements ContextSource<I, O> {

        public void close() {
            receiveServiceTerminate();
        }

        public Context<I, O> createContext() throws RemotingException {
            // Don't need waitForNotHold here since the state can't change again
            final State currentState = state.waitForNot(State.WAITING_FOR_REPLY);
            switch (currentState) {
                case UP: break;
                case FAILED: throw new RemotingException("Context source open failed");
                default:
                    throw new IllegalStateException("Context source is not open");
            }
            final CoreOutboundContext<I, O> context = coreSession.createContext(serviceIdentifier);
            return context.getUserContext();
        }
    }
}

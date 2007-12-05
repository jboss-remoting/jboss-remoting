package org.jboss.cx.remoting.core;

import static org.jboss.cx.remoting.core.util.CollectionUtil.hashMap;
import org.jboss.cx.remoting.ContextSource;
import org.jboss.cx.remoting.Context;
import org.jboss.cx.remoting.RemotingException;
import org.jboss.cx.remoting.ServiceLocator;
import org.jboss.cx.remoting.spi.protocol.ServiceIdentifier;
import org.jboss.cx.remoting.spi.protocol.ProtocolHandler;
import org.jboss.cx.remoting.spi.protocol.ContextIdentifier;
import java.io.IOException;
import java.util.Map;

/**
 *
 */
public final class CoreRemoteService<I, O> {
    private final Map<ServiceLocator<I, O>, Service> eligableLocators = hashMap();
    

    public final class Service {
        private final ServiceLocator<I, O> locator;
        private final CoreSession coreSession;
        private final ProtocolHandler protocolHandler;
        private final ServiceIdentifier serviceIdentifier;

        private Service(final ServiceLocator<I, O> locator, final CoreSession coreSession, final ServiceIdentifier serviceIdentifier) {
            this.locator = locator;
            this.coreSession = coreSession;
            this.serviceIdentifier = serviceIdentifier;
            protocolHandler = coreSession.getProtocolHandler();
        }
    }

    public CoreRemoteService() {
    }

    public final class UserContextSource implements ContextSource<I, O> {

        public void close() {
            for (Service s : eligableLocators.values()) {
                try {
                    s.protocolHandler.closeService(s.serviceIdentifier);
                } catch (IOException e) {
                    // todo log it
                }
            }
        }

        protected Service getServiceToUse() {
            int t = (int) (Math.random() * (double)eligableLocators.size());
            for (Service s : eligableLocators.values()) {
                if (t -- == 0) {
                    return s;
                }
            }
            throw new IllegalStateException("Illegal state");
        }

        public Context<I, O> createContext() throws RemotingException {
            try {
                final Service service = getServiceToUse();
                final ProtocolHandler protocolHandler = service.protocolHandler;
                final ServiceIdentifier serviceIdentifier = service.serviceIdentifier;
                final CoreSession coreSession = service.coreSession;
                final ContextIdentifier contextIdentifier = protocolHandler.openContext(serviceIdentifier);
                final CoreContext<I,O> coreContext = new CoreContext<I,O>(coreSession, contextIdentifier);
                return coreContext.getUserContext();
            } catch (IOException e) {
                throw new RemotingException("Failed to create context", e);
            }
        }
    }


}

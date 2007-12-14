package org.apache.mina.filter.sasl;

import javax.security.auth.callback.CallbackHandler;

/**
 *
 */
public interface CallbackHandlerFactory {
    CallbackHandler getCallbackHandler();
}

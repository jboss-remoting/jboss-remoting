package org.jboss.cx.remoting.core;

/**
 * Standard control message types.
 */
public interface MessageTypes {

    byte CTL_CLOSE = 0;

    byte MESSAGE = 1;
    byte REQUEST = 2;
    byte REQUEST_REPLY = 3;
    byte REQUEST_FAILED = 4;
    byte REQUEST_ACK = 5;
    byte REQUEST_CANCEL = 6;
    byte REQUEST_CANCEL_INTERRUPT = 7;
}

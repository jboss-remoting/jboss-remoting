package org.jboss.cx.remoting.core.security.sasl;

import javax.security.auth.callback.Callback;

/**
 *
 */
public final class SrpVerifierCallback implements Callback {
    private SrpVerifier verifier;

    public SrpVerifierCallback() {
    }

    public SrpVerifier getVerifier() {
        return verifier;
    }

    public void setVerifier(final SrpVerifier verifier) {
        this.verifier = verifier;
    }
}

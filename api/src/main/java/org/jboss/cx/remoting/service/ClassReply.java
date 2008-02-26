package org.jboss.cx.remoting.service;

import java.io.Serializable;

/**
 *
 */
public final class ClassReply implements Serializable {
    private static final long serialVersionUID = 1L;

    private byte[] classBytes;

    public ClassReply() {
    }

    public byte[] getClassBytes() {
        return classBytes;
    }

    public void setClassBytes(final byte[] classBytes) {
        this.classBytes = classBytes;
    }
}

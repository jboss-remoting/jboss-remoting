package org.jboss.cx.remoting.spi.protocol;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class NumericStreamIdentifier extends NumericIdentifier implements StreamIdentifier {

    private static final long serialVersionUID = 1L;

    public NumericStreamIdentifier() {
    }

    public NumericStreamIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof NumericStreamIdentifier && super.equals(obj);
    }
}

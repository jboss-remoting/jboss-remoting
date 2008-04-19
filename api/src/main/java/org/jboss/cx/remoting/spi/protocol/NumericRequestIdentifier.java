package org.jboss.cx.remoting.spi.protocol;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class NumericRequestIdentifier extends NumericIdentifier implements RequestIdentifier {

    private static final long serialVersionUID = 1L;

    public NumericRequestIdentifier() {
    }

    public NumericRequestIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof NumericRequestIdentifier && super.equals(obj);
    }
}

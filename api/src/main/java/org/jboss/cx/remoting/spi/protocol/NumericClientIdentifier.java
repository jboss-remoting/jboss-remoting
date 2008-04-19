package org.jboss.cx.remoting.spi.protocol;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class NumericClientIdentifier extends NumericIdentifier implements ClientIdentifier {

    private static final long serialVersionUID = 1L;

    public NumericClientIdentifier() {
    }

    public NumericClientIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof NumericClientIdentifier && super.equals(obj);
    }
}

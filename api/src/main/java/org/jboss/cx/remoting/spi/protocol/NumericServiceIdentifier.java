package org.jboss.cx.remoting.spi.protocol;

/**
 *
 */
@SuppressWarnings ({"EqualsAndHashcode"})
public final class NumericServiceIdentifier extends NumericIdentifier implements ServiceIdentifier {

    private static final long serialVersionUID = 1L;

    public NumericServiceIdentifier() {
    }

    public NumericServiceIdentifier(final boolean client, final int id) {
        super(client, id);
    }

    public boolean equals(Object obj) {
        return obj instanceof NumericServiceIdentifier && super.equals(obj);
    }
}

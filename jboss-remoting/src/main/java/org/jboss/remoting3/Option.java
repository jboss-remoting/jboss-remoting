/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, JBoss Inc., and individual contributors as indicated
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.remoting3;

import java.util.Collection;

/**
 * A strongly-typed option to configure an aspect of a service.  Options are immutable and use identity comparisons
 * and hash codes, and they are not serializable.
 *
 * @param <T> the option value type
 */
public abstract class Option<T> {

    private final String name;

    Option(final String name) {
        if (name == null) {
            throw new NullPointerException("name is null");
        }
        this.name = name;
    }

    /**
     * Create an option with a simple type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param name the name of this option
     * @param type the class of the value associated with this option
     * @param <T> the type of the value associated with this option
     * @return the option instance
     */
    public static <T> Option<T> simple(final String name, final Class<T> type) {
        return new SingleOption<T>(name, type);
    }

    /**
     * Create an option with a sequence type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param name the name of this option
     * @param elementType the class of the sequence element value associated with this option
     * @param <T> the type of the sequence element value associated with this option
     * @return the option instance
     */
    public static <T> Option<Sequence<T>> sequence(final String name, final Class<T> elementType) {
        return new SequenceOption<T>(name, elementType);
    }

    /**
     * Create an option with a flag set type.  The class object given <b>must</b> represent some immutable type, otherwise
     * unexpected behavior may result.
     *
     * @param name the name of this option
     * @param elementType the class of the flag values associated with this option
     * @param <T> the type of the flag values associated with this option
     * @return the option instance
     */
    public static <T extends Enum<T>> Option<FlagSet<T>> flags(final String name, final Class<T> elementType) {
        return new FlagsOption<T>(name, elementType);
    }

    /**
     * Get the name of this option.
     *
     * @return the option name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the given object as the type of this option.  If the cast could not be completed, an exception is thrown.
     *
     * @param o the object to cast
     * @return the cast object
     * @throws ClassCastException if the object is not of a compatible type
     */
    public abstract T cast(Object o) throws ClassCastException;
}

final class SingleOption<T> extends Option<T> {

    private final Class<T> type;

    SingleOption(final String name, final Class<T> type) {
        super(name);
        this.type = type;
    }

    public T cast(final Object o) {
        return type.cast(o);
    }
}

final class SequenceOption<T> extends Option<Sequence<T>> {
    private final Class<T> elementType;

    SequenceOption(final String name, final Class<T> elementType) {
        super(name);
        this.elementType = elementType;
    }

    public Sequence<T> cast(final Object o) {
        if (o instanceof Sequence) {
            return ((Sequence<?>)o).cast(elementType);
        } else if (o instanceof Object[]){
            return Sequence.of((Object[])o).cast(elementType);
        } else if (o instanceof Collection) {
            return Sequence.of((Collection<?>)o).cast(elementType);
        } else {
            throw new ClassCastException("Not a sequence");
        }
    }
}

final class FlagsOption<T extends Enum<T>> extends Option<FlagSet<T>> {

    private final Class<T> elementType;

    FlagsOption(final String name, final Class<T> elementType) {
        super(name);
        this.elementType = elementType;
    }

    public FlagSet<T> cast(final Object o) throws ClassCastException {
        final FlagSet<?> flagSet = (FlagSet<?>) o;
        return flagSet.cast(elementType);
    }
}
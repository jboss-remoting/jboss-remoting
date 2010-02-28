/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010, JBoss Inc., and individual contributors as indicated
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

package org.jboss.remoting3.remote;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.jboss.xnio.Buffers;
import org.jboss.xnio.Option;
import org.jboss.xnio.OptionMap;
import org.jboss.xnio.Options;
import org.jboss.xnio.SaslQop;
import org.jboss.xnio.Sequence;

import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;

final class SaslUtils {

    private SaslUtils() {
    }

    static final byte[] EMPTY = new byte[0];

    static Map<String, Object> createPropertyMap(OptionMap optionMap) {
        final Map<String,Object> propertyMap = new HashMap<String, Object>();

        add(optionMap, Options.SASL_POLICY_FORWARD_SECRECY, propertyMap, Sasl.POLICY_FORWARD_SECRECY);
        add(optionMap, Options.SASL_POLICY_NOACTIVE, propertyMap, Sasl.POLICY_NOACTIVE);
        add(optionMap, Options.SASL_POLICY_NOANONYMOUS, propertyMap, Sasl.POLICY_NOANONYMOUS);
        add(optionMap, Options.SASL_POLICY_NODICTIONARY, propertyMap, Sasl.POLICY_NODICTIONARY);
        add(optionMap, Options.SASL_POLICY_NOPLAINTEXT, propertyMap, Sasl.POLICY_NOPLAINTEXT);
        add(optionMap, Options.SASL_POLICY_PASS_CREDENTIALS, propertyMap, Sasl.POLICY_PASS_CREDENTIALS);
        add(optionMap, Options.SASL_REUSE, propertyMap, Sasl.REUSE);
        add(optionMap, Options.SASL_SERVER_AUTH, propertyMap, Sasl.SERVER_AUTH);
        addQopList(optionMap, Options.SASL_QOP, propertyMap, Sasl.QOP);
        add(optionMap, Options.SASL_STRENGTH, propertyMap, Sasl.STRENGTH);
        return propertyMap;
    }

    private static void add(OptionMap optionMap, Option<?> option, Map<String, Object> map, String propName) {
        final Object value = optionMap.get(option);
        if (value != null) map.put(propName, value.toString().toLowerCase());
    }

    private static void addQopList(OptionMap optionMap, Option<Sequence<SaslQop>> option, Map<String, Object> map, String propName) {
        final Sequence<SaslQop> seq = optionMap.get(option);
        final StringBuilder builder = new StringBuilder();
        final Iterator<SaslQop> iterator = seq.iterator();
        while (iterator.hasNext()) {
            builder.append(iterator.next());
            if (iterator.hasNext()) {
                builder.append(',');
            }
        }
    }

    private static final Set<String> SECURE_QOP;

    static {
        final Set<String> set = new HashSet<String>();
        set.add("auth-int");
        set.add("auth-conf");
        SECURE_QOP = set;
    }

    static boolean isSecureQop(Object qop) {
        return SECURE_QOP.contains(qop);
    }

    static void wrapFramed(SaslClient saslClient, ByteBuffer message) throws SaslException {
        final byte[] result;
        if (message.hasArray()) {
            result = saslClient.wrap(message.array(), message.arrayOffset() + 4, message.position());
        } else {
            final int end = message.position();
            message.position(4);
            final byte[] bytes = Buffers.take(message, end - 4);
            result = saslClient.wrap(bytes, 0, bytes.length);
        }
        message.position(4);
        message.put(result);
    }
}

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.remoting3;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.wildfly.client.config.ClientConfiguration;
import org.wildfly.client.config.ConfigXMLParseException;
import org.wildfly.client.config.ConfigurationXMLStreamReader;
import org.wildfly.common.Assert;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.XnioWorker;

import javax.xml.stream.XMLStreamConstants;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class RemotingXmlParser {
    private static final String NS_REMOTING_5_0 = "urn:jboss-remoting:5.0";
    private static final String NS_REMOTING_5_1 = "urn:jboss-remoting:5.1";

    private static final String NS_REMOTING_5_2 = "urn:jboss-remoting:5.2";

    private static final Set<String> validNamespaces = new HashSet<>(Arrays.asList(NS_REMOTING_5_0, NS_REMOTING_5_1, NS_REMOTING_5_2));

    private RemotingXmlParser() {
    }

    static Endpoint parseEndpoint() throws ConfigXMLParseException, IOException {
        final ClientConfiguration clientConfiguration = ClientConfiguration.getInstance();
        final EndpointBuilder builder = new EndpointBuilder();
        builder.setXnioWorker(XnioWorker.getContextManager().get());
        if (clientConfiguration != null) try (final ConfigurationXMLStreamReader streamReader = clientConfiguration.readConfiguration(validNamespaces)) {
            parseDocument(streamReader, builder);
            return builder.build();
        } else {
            return null;
        }
    }

    private static void parseDocument(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {
        if (reader.hasNext()) switch (reader.nextTag()) {
            case START_ELEMENT: {
                checkElementNamespace(reader);
                switch (reader.getLocalName()) {
                    case "endpoint": {
                        parseEndpointElement(reader, builder);
                        break;
                    }
                    default: throw reader.unexpectedElement();
                }
                break;
            }
            default: {
                throw reader.unexpectedContent();
            }
        }
    }

    private static void parseEndpointElement(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {

        OptionMap.Builder optionMapBuilder = OptionMap.builder();

        if( reader.hasNamespace()) {
            switch (reader.getNamespaceURI()) {
                case NS_REMOTING_5_0:{
                    parseEndpointElement50(reader, builder, optionMapBuilder);
                    break;
                }
                case NS_REMOTING_5_1:
                case NS_REMOTING_5_2:{
                    parseEndpointElement51(reader, builder, optionMapBuilder);
                    break;
                }
                default: {
                    throw reader.unexpectedElement();
                }
            }

            while (reader.hasNext()) {
                switch (reader.nextTag()) {
                    case START_ELEMENT: {
                        checkElementNamespace(reader);
                        switch (reader.getLocalName()) {
                            case "providers": {
                                parseProvidersElement(reader, builder);
                                break;
                            }
                            case "connections": {
                                parseConnectionsElement(reader, builder);
                                break;
                            }
                            case "options": {
                                if(reader.getNamespaceURI().equals(NS_REMOTING_5_2)) {
                                    parseEndpointOptions(reader, optionMapBuilder);
                                } else {
                                    throw reader.unexpectedElement();
                                }
                                break;
                            }
                            default: throw reader.unexpectedElement();
                        }
                        break;
                    }
                    case END_ELEMENT: {
                        builder.setDefaultConnectionsOptionMap(optionMapBuilder.getMap());
                        return;
                    }
                }
            }
            throw reader.unexpectedDocumentEnd();
        } else {
            throw reader.unexpectedDocumentEnd();
        }
    }

    private static void parseEndpointElement51(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder, OptionMap.Builder optionMapBuilder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        int readTimeout = -1;
        int writeTimeout = -1;
        boolean tcpKeepAlive = false;
        boolean setTcpKeepAlive = false;
        int heartbeatInterval = -1;
        for (int i = 0; i < attributeCount; i++) {
            checkAttributeNamespace(reader, i);
            switch (reader.getAttributeLocalName(i)) {
                case "name": {
                    builder.setEndpointName(reader.getAttributeValueResolved(i));
                    break;
                }
                case "read-timeout": {
                    readTimeout = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                case "write-timeout": {
                    writeTimeout = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                case "tcp-keepalive": {
                    setTcpKeepAlive = true;
                    tcpKeepAlive = reader.getBooleanAttributeValueResolved(i);
                    break;
                }
                case "heartbeat-interval": {
                    heartbeatInterval = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (readTimeout != -1L) {
            optionMapBuilder.set(Options.READ_TIMEOUT, readTimeout);
        }
        if (writeTimeout != -1L) {
            optionMapBuilder.set(Options.WRITE_TIMEOUT, writeTimeout);
        }
        if (setTcpKeepAlive) {
            optionMapBuilder.set(Options.KEEP_ALIVE, tcpKeepAlive);
        }
        if (heartbeatInterval != -1) {
            optionMapBuilder.set(RemotingOptions.HEARTBEAT_INTERVAL, heartbeatInterval);
        }
    }

    protected static void parseEndpointOptions(final ConfigurationXMLStreamReader reader, OptionMap.Builder optionMapBuilder) throws ConfigXMLParseException {
        while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
            switch (reader.getLocalName()) {
                case "option": {
                    parseEndpointOption(reader, optionMapBuilder);
                    break;
                }
                default: {
                    throw reader.unexpectedElement();
                }
            }
        }
    }

    protected static void parseEndpointOption(final ConfigurationXMLStreamReader reader, OptionMap.Builder optionMapBuilder) throws ConfigXMLParseException {
        final int count = reader.getAttributeCount();
        String optionName = null;
        String optionType = null;
        String optionValue = null;
        for (int i = 0; i < count; i++) {
            final String attribute = reader.getAttributeLocalName(i);
            final String attributeValue = reader.getAttributeValue(i);
            switch (attribute) {
                case "name":
                    optionName = attributeValue;
                    break;
                case "type":
                    optionType = attributeValue;
                    if (!(optionType.equals("remoting") || optionType.equals("xnio"))) {
                        throw reader.unexpectedAttribute(i);
                    }
                    break;
                case "value":
                    optionValue = attributeValue;
                    break;
                default:
                    throw reader.unexpectedAttribute(i);
            }
        }
        final ClassLoader loader = RemotingXmlParser.class.getClassLoader();
        final String optionClassName = getClassNameForChannelOptionType(optionType);
        final String fullyQualifiedOptionName = optionClassName + "." + optionName;
        final Option option = Option.fromString(fullyQualifiedOptionName, loader);
        optionMapBuilder.set(option, option.parseValue(optionValue, loader));
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static String getClassNameForChannelOptionType(final String optionType) {
        if ("remoting".equals(optionType)) {
            return RemotingOptions.class.getName();
        }
        if ("xnio".equals(optionType)) {
            return Options.class.getName();
        }
        throw Assert.unreachableCode();
    }


    private static void parseEndpointElement50(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder, OptionMap.Builder optionMapBuilder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();

        for (int i = 0; i < attributeCount; i++) {
            checkAttributeNamespace(reader, i);
            switch (reader.getAttributeLocalName(i)) {
                case "name": {
                    builder.setEndpointName(reader.getAttributeValueResolved(i));
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
    }

    private static void parseProvidersElement(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {
        expectNoAttributes(reader);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    checkElementNamespace(reader);
                    switch (reader.getLocalName()) {
                        case "provider": {
                            parseProviderElement(reader, builder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
        throw reader.unexpectedDocumentEnd();
    }

    private static void expectNoAttributes(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        if (attributeCount > 0) {
            throw reader.unexpectedAttribute(0);
        }
    }

    private static void parseProviderElement(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        String scheme = null;
        String[] aliases = null;
        String module = null;
        String clazz = null;
        for (int i = 0; i < attributeCount; i ++) {
            final String attributeNamespace = reader.getAttributeNamespace(i);
            if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
                throw reader.unexpectedAttribute(i);
            }
            switch (reader.getAttributeLocalName(i)) {
                case "scheme": {
                    scheme = reader.getAttributeValueResolved(i);
                    break;
                }
                case "aliases": {
                    aliases = reader.getListAttributeValueAsArrayResolved(i);
                    break;
                }
                case "module": {
                    module = reader.getAttributeValueResolved(i);
                    break;
                }
                case "class": {
                    clazz = reader.getAttributeValueResolved(i);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        final ConnectionProviderFactoryBuilder providerBuilder = builder.addProvider(scheme);
        if (aliases != null) for (String alias : aliases) {
            providerBuilder.addAlias(alias);
        }
        if (module == null && clazz == null) {
            throw new ConfigXMLParseException("At least one of the 'module' or 'class' attributes must be given", reader);
        }
        if (module != null) {
            providerBuilder.setModuleName(module);
        }
        if (clazz != null) {
            providerBuilder.setClassName(clazz);
        }
        switch (reader.nextTag()) {
            case END_ELEMENT: {
                return;
            }
            default: {
                throw reader.unexpectedElement();
            }
        }
    }

    private static void parseConnectionsElement(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {
        expectNoAttributes(reader);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case START_ELEMENT: {
                    checkElementNamespace(reader);
                    switch (reader.getLocalName()) {
                        case "connection": {
                            parseConnectionElement(reader, builder);
                            break;
                        }
                        default: throw reader.unexpectedElement();
                    }
                    break;
                }
                case END_ELEMENT: {
                    return;
                }
            }
        }
        throw reader.unexpectedDocumentEnd();
    }

    private static void parseConnectionElement(final ConfigurationXMLStreamReader reader, final EndpointBuilder builder) throws ConfigXMLParseException {
        final int attributeCount = reader.getAttributeCount();
        String destination = null;
        int readTimeout = -1;
        int writeTimeout = -1;
        int ipTrafficClass = -1;
        boolean setTcpKeepAlive = false;
        boolean tcpKeepAlive = false;
        int heartbeatInterval = -1;
        for (int i = 0; i < attributeCount; i++) {
            checkAttributeNamespace(reader, i);
            switch (reader.getAttributeLocalName(i)) {
                case "destination": {
                    destination = reader.getAttributeValueResolved(i);
                    break;
                }
                case "read-timeout": {
                    readTimeout = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                case "write-timeout": {
                    writeTimeout = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                case "ip-traffic-class": {
                    ipTrafficClass = reader.getIntAttributeValueResolved(i, 0, 255);
                    break;
                }
                case "tcp-keepalive": {
                    setTcpKeepAlive = true;
                    tcpKeepAlive = reader.getBooleanAttributeValueResolved(i);
                    break;
                }
                case "heartbeat-interval": {
                    heartbeatInterval = reader.getIntAttributeValueResolved(i, 0, Integer.MAX_VALUE);
                    break;
                }
                default: {
                    throw reader.unexpectedAttribute(i);
                }
            }
        }
        if (destination == null) {
            throw reader.missingRequiredAttribute("", "destination");
        }
        final ConnectionBuilder connectionBuilder = builder.addConnection(URI.create(destination));
        if (readTimeout != -1L) {
            connectionBuilder.setReadTimeout(readTimeout);
        }
        if (writeTimeout != -1L) {
            connectionBuilder.setWriteTimeout(writeTimeout);
        }
        if (ipTrafficClass != -1) {
            connectionBuilder.setIpTrafficClass(ipTrafficClass);
        }
        if (setTcpKeepAlive) {
            connectionBuilder.setTcpKeepAlive(tcpKeepAlive);
        }
        if (heartbeatInterval != -1) {
            connectionBuilder.setHeartbeatInterval(heartbeatInterval);
        }
        if (reader.nextTag() != END_ELEMENT) {
            throw reader.unexpectedContent();
        }
    }

    private static void checkAttributeNamespace(final ConfigurationXMLStreamReader reader, final int idx) throws ConfigXMLParseException {
        final String attributeNamespace = reader.getAttributeNamespace(idx);
        if (attributeNamespace != null && ! attributeNamespace.isEmpty()) {
            throw reader.unexpectedAttribute(idx);
        }
    }

    private static void checkElementNamespace(final ConfigurationXMLStreamReader reader) throws ConfigXMLParseException {
        switch (reader.getNamespaceURI()) {
            case NS_REMOTING_5_0:
            case NS_REMOTING_5_1:
            case NS_REMOTING_5_2:
                break;
            default: throw reader.unexpectedElement();
        }
    }
}

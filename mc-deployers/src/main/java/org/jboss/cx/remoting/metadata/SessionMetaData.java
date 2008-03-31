package org.jboss.cx.remoting.metadata;

import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.cx.remoting.util.AttributeMap;
import org.jboss.cx.remoting.util.AttributeHashMap;
import org.jboss.cx.remoting.util.AttributeKey;
import org.jboss.cx.remoting.util.CollectionUtil;
import org.jboss.cx.remoting.CommonKeys;
import java.io.Serializable;
import java.util.List;
import java.net.URI;
import java.lang.reflect.Field;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;

/**
 * Metadata which describes a session to be established and maintained with another endpoint.
 */
@XmlType(namespace = "urn:jboss:remoting:3.0", name = "session")
public final class SessionMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String endpoint;
    private URI destination;
    private List<SessionMetaDataAttribute> attributeList;

    public String getName() {
        return name;
    }

    @XmlAttribute(required = true)
    public void setName(final String name) {
        this.name = name;
    }

    public String getEndpoint() {
        return endpoint;
    }

    @XmlAttribute(required = true)
    public void setEndpoint(final String endpoint) {
        this.endpoint = endpoint;
    }

    public URI getDestination() {
        return destination;
    }

    @XmlAttribute(required = true)
    public void setDestination(final URI destination) {
        this.destination = destination;
    }

    public List<SessionMetaDataAttribute> getAttributeList() {
        return attributeList;
    }

    @XmlElement(name = "attribute")
    public void setAttributeList(final List<SessionMetaDataAttribute> attributeList) {
        this.attributeList = attributeList;
    }

    public SessionMetaData clone() throws CloneNotSupportedException {
        return (SessionMetaData) super.clone();
    }

    public List<BeanMetaData> getBeans() {
        return CollectionUtil.unmodifiableList(createSessionMetaData());
    }

    @SuppressWarnings({"unchecked"})
    private static <T> void putAttribute(AttributeMap attributeMap, AttributeKey<T> key, Object value) {
        attributeMap.put(key, (T) value);
    }

    private BeanMetaData createSessionMetaData() {
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(name);
        builder.addPropertyMetaData("endpoint", builder.createInject(endpoint));
        builder.addPropertyMetaData("destination", destination);
        final AttributeMap attributeMap = new AttributeHashMap();
        try {
            for (SessionMetaDataAttribute attribute : attributeList) {
                Class<?> claxx = attribute.getClaxx();
                if (claxx == null) {
                    claxx = CommonKeys.class;
                }
                final String fieldName = attribute.getName();
                final Field field = claxx.getDeclaredField(fieldName);
                final AttributeKey<?> keyInstance = (AttributeKey<?>) field.get(null);
                putAttribute(attributeMap, keyInstance, attribute.getValue());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error reading field", e);
        }
        builder.addPropertyMetaData("attributeMap", attributeMap);
        return builder.getBeanMetaData();
    }
}

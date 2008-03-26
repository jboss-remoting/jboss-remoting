package org.jboss.cx.remoting.metadata;

import java.io.Serializable;
import java.util.List;
import org.jboss.beans.metadata.plugins.AbstractInjectionValueMetaData;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.cx.remoting.util.CollectionUtil;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;

/**
 *
 */
@XmlType(name = "endpoint")
public class EndpointMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String executorName;
    private String rootRequestListenerName;

    public String getName() {
        return name;
    }

    @XmlAttribute
    public void setName(final String name) {
        this.name = name;
    }

    public String getExecutorName() {
        return executorName;
    }

    @XmlAttribute(name = "executor")
    public void setExecutorName(final String executorName) {
        this.executorName = executorName;
    }

    public String getRootRequestListenerName() {
        return rootRequestListenerName;
    }

    @XmlAttribute(name = "rootRequestListener")
    public void setRootRequestListenerName(final String rootRequestListenerName) {
        this.rootRequestListenerName = rootRequestListenerName;
    }

    public EndpointMetaData clone() throws CloneNotSupportedException {
        return (EndpointMetaData) super.clone();
    }

    public List<BeanMetaData> getBeans() {
        final String userEndpointName = "Endpoint:" + name;
        final String coreEndpointName = "CoreEndpoint:" + name;
        final String jrppProtocolSupportName = "JrppProtocolSupport:" + name;

        BeanMetaDataBuilder builder;
        builder = BeanMetaDataBuilder.createBuilder(coreEndpointName);
        builder.addConstructorParameter("java.lang.String", name);
        builder.addConstructorParameter("org.jboss.cx.remoting.RequestListener", builder.createInject(rootRequestListenerName));
        builder.addPropertyMetaData("executor", builder.createInject(executorName));
        final BeanMetaData coreEndpointMetaData = builder.getBeanMetaData();
        builder = BeanMetaDataBuilder.createBuilder(userEndpointName);
        builder.setFactory(coreEndpointName, "userEndpoint");
        final BeanMetaData endpointMetaData = builder.getBeanMetaData();
        builder = BeanMetaDataBuilder.createBuilder(jrppProtocolSupportName);
        builder.addPropertyMetaData("executor", builder.createInject(executorName));
        builder.addPropertyMetaData("endpoint", builder.createInject(userEndpointName));
        final BeanMetaData jrppSupportMetaData = builder.getBeanMetaData();

        return CollectionUtil.unmodifiableList(coreEndpointMetaData, endpointMetaData, jrppSupportMetaData);
    }
}

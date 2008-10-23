package org.jboss.remoting.metadata;

import java.io.Serializable;
import java.util.List;
import org.jboss.beans.metadata.spi.BeanMetaData;
import org.jboss.beans.metadata.spi.BeanMetaDataFactory;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.remoting.util.CollectionUtil;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/**
 * Metadata that describes the creation of a Remoting endpoint.
 *
 * @see org.jboss.remoting.Endpoint
 */
@XmlType(name = "endpoint")
public class EndpointMetaData implements BeanMetaDataFactory, Serializable {
    private static final long serialVersionUID = 1L;

    private String name;
    private String executorName;
    private String rootRequestListenerName;

    /**
     * Get the name of the endpoint to be created.
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the name of the endpoint to be created.
     *
     * @param name
     */
    @XmlAttribute
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Get the name of the executor to use.  The name should be associated with a bean that implements
     * {@link java.util.concurrent.Executor}.
     *
     * @return the name
     */
    public String getExecutorName() {
        return executorName;
    }

    /**
     * Set the name of the executor to use.  The name should be associated with a bean that implements
     * {@link java.util.concurrent.Executor}.
     *
     * @param executorName the name
     */
    @XmlAttribute(name = "executor")
    public void setExecutorName(final String executorName) {
        this.executorName = executorName;
    }

    /**
     * Get the name of the root request listener bean for this endpoint.  The name should be associated with a bean
     * that implements {@link org.jboss.remoting.RequestListener}.
     *
     * @return the name of root request listener bean
     */
    public String getRootRequestListenerName() {
        return rootRequestListenerName;
    }

    /**
     * Set the name of the root request listener bean for this endpoint.  The name should be associated with a bean
     * that implements {@link org.jboss.remoting.RequestListener}.
     *
     * @param rootRequestListenerName the name of the root request listener bean
     */
    @XmlAttribute(name = "rootRequestListener")
    public void setRootRequestListenerName(final String rootRequestListenerName) {
        this.rootRequestListenerName = rootRequestListenerName;
    }

    /**
     * Create a duplicate of this metadata object.
     *
     * @return a duplicate
     */
    public EndpointMetaData clone() throws CloneNotSupportedException {
        return (EndpointMetaData) super.clone();
    }

    /**
     * Get all the metadata objects required to deploy an endpoint.
     *
     * @return the metadata objects
     */
    @XmlTransient
    public List<BeanMetaData> getBeans() {
        final String userEndpointName = "Endpoint:" + name;
        final String coreEndpointName = "CoreEndpoint:" + name;
        final String jrppProtocolSupportName = "JrppProtocolSupport:" + name;

        final BeanMetaData coreEndpointMetaData = buildCoreEndpointMetaData(coreEndpointName);
        final BeanMetaData endpointMetaData = buildEndpointMetaData(userEndpointName, coreEndpointName);
        final BeanMetaData jrppSupportMetaData = buildJrppProtocolSupportMetaData(userEndpointName, jrppProtocolSupportName);

        return CollectionUtil.unmodifiableList(coreEndpointMetaData, endpointMetaData, jrppSupportMetaData);
    }

    private BeanMetaData buildCoreEndpointMetaData(final String coreEndpointName) {
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(coreEndpointName);
        builder.addConstructorParameter("java.lang.String", name);
        builder.addConstructorParameter("org.jboss.remoting.RequestListener", builder.createInject(rootRequestListenerName));
        builder.addPropertyMetaData("executor", builder.createInject(executorName));
        return builder.getBeanMetaData();
    }

    private BeanMetaData buildEndpointMetaData(final String userEndpointName, final String coreEndpointName) {
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(userEndpointName);
        builder.setFactory(coreEndpointName, "userEndpoint");
        return builder.getBeanMetaData();
    }

    private BeanMetaData buildJrppProtocolSupportMetaData(final String userEndpointName, final String jrppProtocolSupportName) {
        final BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(jrppProtocolSupportName);
        builder.addPropertyMetaData("executor", builder.createInject(executorName));
        builder.addPropertyMetaData("endpoint", builder.createInject(userEndpointName));
        return builder.getBeanMetaData();
    }
}

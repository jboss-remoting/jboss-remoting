package org.jboss.cx.remoting.http.urlconnection;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 *
 */
public final class HttpsUrlChannel extends AbstractHttpUrlChannel {

    // Configuration

    private HostnameVerifier hostnameVerifier;
    private SSLSocketFactory sslSocketFactory;

    public HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    public void setHostnameVerifier(final HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }

    public SSLSocketFactory getSslSocketFactory() {
        return sslSocketFactory;
    }

    public void setSslSocketFactory(final SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    // Dependencies

    

    // Lifecycle

    public void create() {
        super.create();
        if (hostnameVerifier == null) {
            hostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        }
        if (sslSocketFactory == null) {
            sslSocketFactory = HttpsURLConnection.getDefaultSSLSocketFactory();
        }
        final String protocol = getConnectUrl().getProtocol();
        if (! "https".equals(protocol)) {
            throw new IllegalArgumentException("Cannot use " + HttpsUrlChannel.class.getName() + " for protocol \"" + protocol + "\"");
        }
    }

    public void start() {
        super.start();
    }

    public void stop() {
        super.stop();
    }

    public void destroy() {
        try {
            super.destroy();
        } finally {
            hostnameVerifier = null;
            sslSocketFactory = null;
        }
    }

    // Interface

    protected HttpsURLConnection intializeConnection(final URL connectUrl) throws IOException {
        final HttpsURLConnection httpsURLConnection = (HttpsURLConnection) super.intializeConnection(connectUrl);
        httpsURLConnection.setHostnameVerifier(hostnameVerifier);
        httpsURLConnection.setSSLSocketFactory(sslSocketFactory);
        return httpsURLConnection;
    }
}

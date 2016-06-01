package org.jboss.remoting3.test.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;

import org.jboss.logging.Logger;

public final class InetAddressUtil {

    private static final Logger logger = Logger.getLogger(InetAddressUtil.class);

    private static final int LOCAL_PORT = 30123;
    private static final String LOCAL_IPv4_ADDRESS = "127.0.0.1";
    private static final String LOCAL_IPv6_ADDRESS = "[::1]";

    private static boolean IPv4Supported;

    private static boolean IPv6Supported;

    private InetAddressUtil () {}

    static {
        IPv4Supported = check(LOCAL_IPv4_ADDRESS, LOCAL_PORT);
        IPv6Supported = check(LOCAL_IPv6_ADDRESS, LOCAL_PORT);
    }

    public static boolean check(String ip, int port) {
        try(Socket socket = new Socket()) {
            socket.bind(new InetSocketAddress(ip, port));
            return true;
        } catch (IOException e) {
            logger.errorf(e, "address not supported %s:%d", ip, port);
            return false;
        }
    }

    public static boolean isIPv4supported() {
        return IPv4Supported;
    }

    public static boolean isIPv6supported() {
        return IPv6Supported;
    }

    public static InetSocketAddress getLocalInetSocketAddress() {
        return new InetSocketAddress((IPv6Supported) ? LOCAL_IPv6_ADDRESS : LOCAL_IPv4_ADDRESS, LOCAL_PORT);
    }

    public static URI getLocalURI() throws URISyntaxException {
        return new URI("remote", null, (IPv6Supported) ? LOCAL_IPv6_ADDRESS : LOCAL_IPv4_ADDRESS, LOCAL_PORT, null, null, null);
    }

}

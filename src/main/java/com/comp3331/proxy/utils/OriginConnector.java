package com.comp3331.proxy.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;

/**
 * Handles connections to origin servers.
 */
public class OriginConnector {
    private final int timeout;
    
    public OriginConnector(int timeout) {
        this.timeout = timeout;
    }
    
    public OriginConnector() {
        this(30); // Default 30 second timeout
    }
    
    /**
     * Connect to origin server.
     * 
     * Returns: Socket object
     * Throws: ProxyException with appropriate message
     */
    public Socket connectToOrigin(String hostname, int port) throws ProxyException {
        try {
            Socket socket = new Socket();
            socket.setSoTimeout(timeout * 1000); // Convert to milliseconds
            
            // Connect with timeout
            InetSocketAddress address = new InetSocketAddress(hostname, port);
            socket.connect(address, timeout * 1000);
            
            return socket;
            
        } catch (UnknownHostException e) {
            throw new ProxyException("could not resolve host: " + hostname);
        } catch (SocketTimeoutException e) {
            throw new ProxyException("connection to " + hostname + " timed out");
        } catch (ConnectException e) {
            if (e.getMessage() != null && e.getMessage().contains("Connection refused")) {
                throw new ProxyException("connection refused by " + hostname);
            } else {
                throw new ProxyException("could not connect to " + hostname);
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Network is unreachable")) {
                throw new ProxyException("network unreachable for host: " + hostname);
            } else {
                throw new ProxyException("connection error: " + e.getMessage());
            }
        }
    }
}
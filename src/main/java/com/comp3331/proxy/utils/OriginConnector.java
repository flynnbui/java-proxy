package com.comp3331.proxy.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

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
            // First try to resolve the hostname with a timeout
            InetAddress address = resolveHostWithTimeout(hostname, 3); // 3 second DNS timeout
            
            Socket socket = new Socket();
            socket.setSoTimeout(timeout * 1000); // Convert to milliseconds
            
            // Connect with resolved address
            InetSocketAddress socketAddress = new InetSocketAddress(address, port);
            socket.connect(socketAddress, timeout * 1000);
            
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
    
    /**
     * Resolve hostname with timeout.
     */
    private InetAddress resolveHostWithTimeout(String hostname, int timeoutSeconds) throws UnknownHostException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<InetAddress> future = executor.submit(() -> {
                try {
                    return InetAddress.getByName(hostname);
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            });
            
            try {
                return future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new UnknownHostException("DNS resolution timeout for: " + hostname);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException && 
                    cause.getCause() instanceof UnknownHostException) {
                    throw (UnknownHostException) cause.getCause();
                }
                throw new UnknownHostException("Failed to resolve: " + hostname);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UnknownHostException("DNS resolution interrupted: " + hostname);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}

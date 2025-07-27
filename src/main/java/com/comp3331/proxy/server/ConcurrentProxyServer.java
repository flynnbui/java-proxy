package com.comp3331.proxy.server;

import com.comp3331.proxy.config.ProxyConfig;
import com.comp3331.proxy.cache.HTTPCache;
import com.comp3331.proxy.http.*;
import com.comp3331.proxy.utils.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Concurrent HTTP proxy server with threading support and caching.
 */
public class ConcurrentProxyServer extends ProxyServer {
    private final int maxWorkers;
    private ExecutorService threadPool;
    private final HTTPCache cache;
    private final AtomicInteger totalConnections;
    private final AtomicInteger activeConnections;
    private final AtomicInteger completedConnections;
    
    public ConcurrentProxyServer(ProxyConfig config, int maxWorkers) {
        super(config);
        this.maxWorkers = maxWorkers;
        this.cache = new HTTPCache(config.getMaxCacheSize(), config.getMaxObjectSize());
        this.totalConnections = new AtomicInteger(0);
        this.activeConnections = new AtomicInteger(0);
        this.completedConnections = new AtomicInteger(0);
    }
    
    @Override
    public void run() throws IOException {
        setupServerSocket();
        threadPool = Executors.newFixedThreadPool(maxWorkers);
        running = true;
        
        System.out.println("Concurrent proxy server started with " + maxWorkers + " max workers");
        
        try {
            acceptConnections();
        } finally {
            cleanup();
        }
    }
    
    /**
     * Accept and handle connections concurrently.
     */
    private void acceptConnections() {
        while (running) {
            try {
                if (serverSocket != null) {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    int clientPort = clientSocket.getPort();
                    
                    System.out.println("New connection from " + clientIp + ":" + clientPort);
                    
                    // Submit connection handling to thread pool
                    threadPool.submit(() -> handleClientConnectionThreaded(clientSocket, clientIp, clientPort));
                    
                    // Update connection statistics
                    totalConnections.incrementAndGet();
                    activeConnections.incrementAndGet();
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Accept error: " + e.getMessage());
                }
                break;
            }
        }
    }
    
    /**
     * Handle client connection in dedicated thread.
     */
    private void handleClientConnectionThreaded(Socket clientSocket, String clientIp, int clientPort) {
        long threadId = Thread.currentThread().getId();
        System.out.println("[Thread " + threadId + "] Handling connection from " + clientIp + ":" + clientPort);
        
        try {
            // Handle persistent connections
            handleClientPersistent(clientSocket, clientIp, clientPort);
            
        } catch (Exception e) {
            System.err.println("[Thread " + threadId + "] Connection error: " + e.getMessage());
        } finally {
            closeSocketSafely(clientSocket);
            
            // Update statistics
            activeConnections.decrementAndGet();
            completedConnections.incrementAndGet();
            
            System.out.println("[Thread " + threadId + "] Connection closed for " + clientIp + ":" + clientPort);
        }
    }
    
    /**
     * Handle persistent connection with multiple requests.
     */
    protected void handleClientPersistent(Socket clientSocket, String clientIp, int clientPort) {
        try {
            clientSocket.setSoTimeout(config.getTimeout() * 1000);
            HTTPStreamReader reader = new HTTPStreamReader(clientSocket, config.getTimeout());
            OutputStream clientOutput = clientSocket.getOutputStream();
            
            while (!clientSocket.isClosed() && running) {
                try {
                    // Read HTTP request
                    HTTPRequest request = reader.readHttpRequest();
                    String requestLine = request.getMethod() + " " + request.getTarget() + " " + request.getVersion();
                    
                    System.out.println("Request: " + requestLine);
                    
                    // Process request
                    byte[] responseData;
                    try {
                        responseData = processRequest(request, clientSocket);
                    } catch (ProxyException | HTTPParseException e) {
                        responseData = ErrorResponseGenerator.badGateway("Request processing failed: " + e.getMessage());
                    }
                    int statusCode = extractStatusCode(responseData);
                    int responseBytes = extractResponseBodySize(responseData);
                    
                    // Send response
                    if (responseData != null) {
                        clientOutput.write(responseData);
                        clientOutput.flush();
                    }
                    
                    // Log transaction with cache status
                    String cacheStatus = getCacheStatus(request);
                    logger.logTransaction(clientIp, clientPort, cacheStatus, requestLine, statusCode, responseBytes);
                    
                    // Check if connection should be closed
                    if (shouldCloseConnection(request, responseData)) {
                        break;
                    }
                    
                } catch (HTTPParseException e) {
                    // Malformed request - close connection
                    try {
                        byte[] errorResponse = ErrorResponseGenerator.badRequest("Malformed request");
                        clientOutput.write(errorResponse);
                        logger.logTransaction(clientIp, clientPort, "-", "", 400, errorResponse.length);
                    } catch (IOException ioE) {
                        // Ignore
                    }
                    break;
                } catch (java.net.SocketTimeoutException e) {
                    // Socket timeout - normal for persistent connections
                    System.out.println("[Thread " + Thread.currentThread().getId() + "] Connection timeout - closing");
                    break;
                } catch (IOException e) {
                    // Connection closed by client
                    if (!e.getMessage().contains("Connection reset")) {
                        System.err.println("[Thread " + Thread.currentThread().getId() + "] IO error: " + e.getMessage());
                    }
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Error in persistent connection: " + e.getMessage());
        }
    }
    
    /**
     * Process a single request with caching support.
     */
    private byte[] processRequest(HTTPRequest request, Socket clientSocket) throws ProxyException, IOException, HTTPParseException {
        try {
            if ("GET".equals(request.getMethod())) {
                return handleGetWithCache(request);
            } else if ("HEAD".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
                return handleHttpMethod(request, "", 0);
            } else if ("CONNECT".equals(request.getMethod())) {
                return handleConnectMethod(request, clientSocket);
            } else {
                return ErrorResponseGenerator.badRequest("Method not supported: " + request.getMethod());
            }
        } catch (ProxyException e) {
            // Handle proxy exceptions with appropriate error responses
            String msg = e.getMessage();
            if (msg.contains("could not resolve") || msg.contains("could not connect")) {
                return ErrorResponseGenerator.badGateway("Failed to resolve host");
            } else if (msg.contains("timed out")) {
                return ErrorResponseGenerator.gatewayTimeout("Connection to origin server timed out");
            } else if (msg.contains("connection refused")) {
                return ErrorResponseGenerator.badGateway("Connection refused by origin server");
            } else if (msg.contains("network unreachable")) {
                return ErrorResponseGenerator.badGateway("Network unreachable");
            } else {
                return ErrorResponseGenerator.badGateway("Proxy error: " + e.getMessage());
            }
        }
    }
    
    /**
     * Handle GET request with caching.
     */
    private byte[] handleGetWithCache(HTTPRequest request) throws ProxyException, IOException, HTTPParseException {
        String cacheKey = cache.normalizeUrl(request.getTarget());
        
        // Check cache first
        HTTPResponse cachedResponse = cache.get(cacheKey);
        if (cachedResponse != null) {
            return transformer.transformResponseForClient(cachedResponse);
        }
        
        // Not in cache - fetch from origin
        String[] urlParts = URLParser.parseAbsoluteUrl(request.getTarget());
        String hostname = urlParts[1];
        int port = Integer.parseInt(urlParts[2]);
        String path = urlParts[3];
        
        // Check for self-loop
        if (URLParser.isSelfLoop(hostname, port, config.getPort())) {
            return ErrorResponseGenerator.misdirectedRequest("Self-loop detected");
        }
        
        // Connect to origin server
        Socket originSocket = connector.connectToOrigin(hostname, port);
        
        try {
            // Transform and send request to origin
            byte[] transformedRequest = transformer.transformRequestForOrigin(request, hostname, port, path);
            originSocket.getOutputStream().write(transformedRequest);
            originSocket.getOutputStream().flush();
            
            // Read response from origin
            HTTPStreamReader originReader = new HTTPStreamReader(originSocket, config.getTimeout());
            HTTPResponse response = originReader.readHttpResponse(request.getMethod());
            
            // Cache response if appropriate
            if (cache.isCacheable(request, response)) {
                cache.put(cacheKey, response, request);
            }
            
            // Transform response for client
            return transformer.transformResponseForClient(response);
            
        } finally {
            closeSocketSafely(originSocket);
        }
    }
    
    /**
     * Handle CONNECT method for HTTPS tunneling.
     */
    @Override
    protected byte[] handleConnectMethod(HTTPRequest request, Socket clientSocket) throws ProxyException {
        try {
            // Parse authority form target
            String[] authorityParts = URLParser.parseAuthorityForm(request.getTarget());
            String hostname = authorityParts[0];
            int port = Integer.parseInt(authorityParts[1]);
            
            // Only allow port 443 for HTTPS
            if (port != 443) {
                return ErrorResponseGenerator.badRequest("Only port 443 allowed for CONNECT");
            }
            
            // Check for self-loop
            if (URLParser.isSelfLoop(hostname, port, config.getPort())) {
                return ErrorResponseGenerator.misdirectedRequest("Self-loop detected");
            }
            
            // Connect to target server
            Socket targetSocket = connector.connectToOrigin(hostname, port);
            
            try {
                // Send 200 Connection Established
                String response = "HTTP/1.1 200 Connection Established\r\n\r\n";
                clientSocket.getOutputStream().write(response.getBytes());
                clientSocket.getOutputStream().flush();
                
                // Start bidirectional tunnel
                relayData(clientSocket, targetSocket);
                
                return null; // No additional response needed
                
            } finally {
                closeSocketSafely(targetSocket);
            }
            
        } catch (Exception e) {
            return ErrorResponseGenerator.badGateway("CONNECT failed: " + e.getMessage());
        }
    }
    
    /**
     * Relay data bidirectionally between client and target.
     */
    private void relayData(Socket clientSocket, Socket targetSocket) {
        ExecutorService relayPool = Executors.newFixedThreadPool(2);
        
        try {
            // Client to target
            relayPool.submit(() -> {
                try {
                    copyStream(clientSocket.getInputStream(), targetSocket.getOutputStream());
                } catch (IOException e) {
                    // Connection closed
                }
            });
            
            // Target to client
            relayPool.submit(() -> {
                try {
                    copyStream(targetSocket.getInputStream(), clientSocket.getOutputStream());
                } catch (IOException e) {
                    // Connection closed
                }
            });
            
            // Wait for either direction to close
            relayPool.shutdown();
            relayPool.awaitTermination(300, TimeUnit.SECONDS); // 5 minute max
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            relayPool.shutdownNow();
        }
    }
    
    /**
     * Copy data from input stream to output stream.
     */
    private void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
        }
    }
    
    /**
     * Get cache status for logging.
     */
    private String getCacheStatus(HTTPRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return "-"; // Non-GET requests
        }
        
        String cacheKey = cache.normalizeUrl(request.getTarget());
        HTTPResponse cached = cache.get(cacheKey);
        return cached != null ? "H" : "M"; // Hit or Miss
    }
    
    /**
     * Check if connection should be closed based on headers.
     */
    private boolean shouldCloseConnection(HTTPRequest request, byte[] responseData) {
        // Check Connection header in request
        String connectionHeader = request.getHeader("connection");
        if ("close".equalsIgnoreCase(connectionHeader)) {
            return true;
        }
        
        // HTTP/1.0 closes by default unless keep-alive
        if ("HTTP/1.0".equals(request.getVersion())) {
            String keepAlive = request.getHeader("connection");
            return !"keep-alive".equalsIgnoreCase(keepAlive);
        }
        
        // Check response for Connection: close
        if (responseData != null) {
            String responseStr = new String(responseData);
            return responseStr.toLowerCase().contains("connection: close");
        }
        
        return false; // HTTP/1.1 keeps alive by default
    }
    
    /**
     * Get connection statistics.
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
            totalConnections.get(),
            activeConnections.get(),
            completedConnections.get()
        );
    }
    
    /**
     * Get cache statistics.
     */
    public HTTPCache.CacheStats getCacheStats() {
        return cache.getStats();
    }
    
    @Override
    public void shutdown() {
        System.out.println("Shutting down concurrent proxy server...");
        running = false;
        
        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        
        // Shutdown thread pool
        if (threadPool != null) {
            int activeCount = activeConnections.get();
            if (activeCount > 0) {
                System.out.println("Waiting for " + activeCount + " active connections to complete...");
            }
            
            threadPool.shutdown();
            try {
                if (!threadPool.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.out.println("Some connections may not have completed - forcing shutdown");
                    threadPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                threadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // Print final statistics
        ConnectionStats connStats = getConnectionStats();
        HTTPCache.CacheStats cacheStats = getCacheStats();
        
        System.out.println("Final Statistics:");
        System.out.println("  Connections - Total: " + connStats.total + ", Completed: " + connStats.completed);
        System.out.println("  Cache - Entries: " + cacheStats.entries + ", Hit Rate: " + 
                         String.format("%.2f%%", cacheStats.hitRate * 100));
        System.out.println("Concurrent proxy server shutdown complete");
    }
    
    /**
     * Connection statistics holder.
     */
    public static class ConnectionStats {
        public final int total;
        public final int active;
        public final int completed;
        
        public ConnectionStats(int total, int active, int completed) {
            this.total = total;
            this.active = active;
            this.completed = completed;
        }
    }
}
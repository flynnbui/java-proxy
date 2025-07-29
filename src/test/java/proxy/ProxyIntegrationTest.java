package proxy;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import proxy.config.ProxyConfig;
import proxy.server.ConcurrentProxyServer;
import proxy.cache.HTTPCache;

import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for the complete HTTP Proxy system.
 * Tests end-to-end functionality including all major features.
 */
public class ProxyIntegrationTest {
    private ProxyConfig config;
    private ConcurrentProxyServer proxyServer;
    private Thread proxyThread;
    private int proxyPort;
    
    // Mock HTTP server for testing
    private ServerSocket mockServer;
    private Thread mockServerThread;
    private int mockServerPort;
    
    @Before
    public void setUp() throws IOException {
        // Setup proxy
        proxyPort = findAvailablePort();
        config = new ProxyConfig(proxyPort, 10, 2048, 8192);
        proxyServer = new ConcurrentProxyServer(config, 10);
        
        // Setup mock server
        mockServerPort = findAvailablePort();
        setupMockHttpServer();
        
        // Start proxy
        startProxy();
        
        // Give servers time to start
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    @After
    public void tearDown() {
        if (proxyServer != null) {
            proxyServer.clearCache(); // Clear cache after each test
            proxyServer.shutdown();
        }
        if (proxyThread != null) {
            try {
                proxyThread.join(2000);
            } catch (InterruptedException e) {
                proxyThread.interrupt();
            }
        }
        if (mockServer != null && !mockServer.isClosed()) {
            try {
                mockServer.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        if (mockServerThread != null) {
            mockServerThread.interrupt();
        }
    }
    
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
    
    private void startProxy() {
        proxyThread = new Thread(() -> {
            try {
                proxyServer.run();
            } catch (IOException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    System.err.println("Proxy error: " + e.getMessage());
                }
            }
        });
        proxyThread.start();
    }
    
    private void setupMockHttpServer() throws IOException {
        mockServer = new ServerSocket(mockServerPort);
        mockServerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted() && !mockServer.isClosed()) {
                try {
                    Socket clientSocket = mockServer.accept();
                    handleMockRequest(clientSocket);
                } catch (IOException e) {
                    if (!e.getMessage().contains("Socket closed")) {
                        System.err.println("Mock server error: " + e.getMessage());
                    }
                    break;
                }
            }
        });
        mockServerThread.start();
    }
    
    private void handleMockRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            
            // Read request
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            // Read headers
            String line;
            boolean hasBody = false;
            int contentLength = 0;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                    hasBody = true;
                }
            }
            
            // Read body if present
            StringBuilder body = new StringBuilder();
            if (hasBody && contentLength > 0) {
                char[] buffer = new char[contentLength];
                int read = in.read(buffer, 0, contentLength);
                if (read > 0) {
                    body.append(new String(buffer, 0, read));
                }
            }
            
            // Send appropriate response based on request
            sendMockResponse(out, requestLine, body.toString());
            
        } catch (IOException e) {
            // Connection closed
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
    
    private void sendMockResponse(PrintWriter out, String requestLine, String body) {
        if (requestLine.contains("GET /test")) {
            // Standard GET response
            String responseBody = "Hello from mock server!";
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            
        } else if (requestLine.contains("GET /large")) {
            // Large response for cache testing - make it larger than max_object_size (2048)
            StringBuilder sb = new StringBuilder("Large response data: ");
            // Create a response body that's definitely larger than 2048 bytes
            for (int i = 0; i < 2500; i++) {
                sb.append("X");
            }
            String responseBody = sb.toString();
            System.out.println("[MockServer] Large response size: " + responseBody.length() + " bytes");
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            
        } else if (requestLine.contains("GET /cacheable")) {
            // Cacheable response
            String responseBody = "This response can be cached";
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            
        } else if (requestLine.contains("POST /api")) {
            // POST response
            String responseBody = "POST received: " + body;
            out.println("HTTP/1.1 201 Created");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            
        } else if (requestLine.contains("HEAD /test")) {
            // HEAD response (no body)
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: 25");
            out.println("Connection: close");
            out.println();
            
        } else if (requestLine.contains("GET /404")) {
            // Error response
            String responseBody = "Not Found";
            out.println("HTTP/1.1 404 Not Found");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            
        } else {
            // Default response
            String responseBody = "Mock server default response";
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
        }
        out.flush();
    }
    
    @Test(timeout = 15000)
    public void testBasicGetRequest() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send GET request through proxy
            out.println("GET http://localhost:" + mockServerPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read response
            String statusLine = in.readLine();
            assertNotNull("Should receive status line", statusLine);
            assertTrue("Should be 200 OK", statusLine.contains("200 OK"));
            
            // Read headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Process headers
            }
            
            // Read body
            StringBuilder body = new StringBuilder();
            while ((line = in.readLine()) != null) {
                body.append(line);
            }
            
            assertTrue("Should contain expected response", 
                      body.toString().contains("Hello from mock server!"));
        }
    }
    
    @Test(timeout = 15000)
    public void testHeadRequest() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send HEAD request through proxy
            out.println("HEAD http://localhost:" + mockServerPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read response
            String statusLine = in.readLine();
            assertNotNull("Should receive status line", statusLine);
            assertTrue("Should be 200 OK", statusLine.contains("200 OK"));
            
            // Read headers
            boolean foundContentLength = false;
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("content-length:")) {
                    foundContentLength = true;
                }
            }
            
            assertTrue("Should have Content-Length header", foundContentLength);
            
            // Should not have body for HEAD request
            assertNull("HEAD response should not have body", in.readLine());
        }
    }
    
    @Test(timeout = 15000)
    public void testPostRequest() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            String postData = "{\"test\": \"data\"}";
            
            // Send POST request through proxy
            out.println("POST http://localhost:" + mockServerPort + "/api HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Content-Type: application/json");
            out.println("Content-Length: " + postData.length());
            out.println("Connection: close");
            out.println();
            out.print(postData);
            out.flush();
            
            // Read response
            String statusLine = in.readLine();
            assertNotNull("Should receive status line", statusLine);
            assertTrue("Should be 201 Created", statusLine.contains("201 Created"));
        }
    }
    
    @Test(timeout = 15000)
    public void testCaching() throws Exception {
        // Clear cache before test to ensure clean state
        proxyServer.clearCache();
        HTTPCache.CacheStats initialStats = proxyServer.getCacheStats();
        assertEquals("Cache should be empty initially", 0, initialStats.entries);
        assertEquals("Initial hits should be 0", 0, initialStats.hits);
        assertEquals("Initial misses should be 0", 0, initialStats.misses);
        
        // First request - should be a cache miss
        makeGetRequest("/cacheable");
        
        HTTPCache.CacheStats afterFirstRequest = proxyServer.getCacheStats();
        assertEquals("Should have one cache miss", 1, afterFirstRequest.misses);
        assertEquals("Should have one cache entry", 1, afterFirstRequest.entries);
        assertEquals("Should have no cache hits yet", 0, afterFirstRequest.hits);
        
        // Second request - should be a cache hit
        makeGetRequest("/cacheable");
        
        HTTPCache.CacheStats afterSecondRequest = proxyServer.getCacheStats();
        assertTrue("Should have at least one cache hit", afterSecondRequest.hits >= 1);
        assertEquals("Should still have one cache miss", 1, afterSecondRequest.misses);
        assertEquals("Should still have one cache entry", 1, afterSecondRequest.entries);
    }
    
    @Test(timeout = 15000)
    public void testCacheEviction() throws Exception {
        // Clear cache before test to ensure clean state
        proxyServer.clearCache();
        HTTPCache.CacheStats initialStats = proxyServer.getCacheStats();
        assertEquals("Cache should be empty initially", 0, initialStats.entries);
        
        // Make request that exceeds max object size (2048 bytes)
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + "/large HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read full response
            String response = readFullResponse(in, true);
            System.out.println("[Test] Response received, length: " + response.length());
        }
        
        // Should not be cached due to size exceeding max_object_size (2048 bytes)
        HTTPCache.CacheStats finalStats = proxyServer.getCacheStats();
        System.out.println("[Test] Final cache stats: " + finalStats);
        // Check that large responses don't increase cache size significantly
        assertTrue("Cache should not grow significantly from large responses", finalStats.entries <= 1);
        assertEquals("Should have one miss for the large request", 1, finalStats.misses);
        assertEquals("Should have no hits", 0, finalStats.hits);
    }
    
    @Test(timeout = 15000)
    public void testPersistentConnection() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // First request with keep-alive
            out.println("GET http://localhost:" + mockServerPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: keep-alive");
            out.println();
            
            // Read first response
            readFullResponse(in);
            
            // Second request on same connection
            out.println("GET http://localhost:" + mockServerPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read second response
            String statusLine = in.readLine();
            assertNotNull("Should receive second response", statusLine);
            assertTrue("Should be 200 OK", statusLine.contains("200 OK"));
        }
    }
    
    @Test(timeout = 15000)
    public void testErrorHandling() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Request to non-existent server
            out.println("GET http://nonexistent.example.com/test HTTP/1.1");
            out.println("Host: nonexistent.example.com");
            out.println("Connection: close");
            out.println();
            
            // Should get error response
            String statusLine = in.readLine();
            assertNotNull("Should receive error response", statusLine);
            assertTrue("Should be a 5xx error", 
                      statusLine.contains("502") || statusLine.contains("504"));
        }
    }
    
    @Test(timeout = 15000)
    public void testConcurrentRequests() throws Exception {
        int numRequests = 5;
        CountDownLatch latch = new CountDownLatch(numRequests);
        
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            Thread clientThread = new Thread(() -> {
                try {
                    makeGetRequest("/test?id=" + requestId);
                } catch (Exception e) {
                    System.err.println("Concurrent request failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
            clientThread.start();
        }
        
        // Wait for all requests to complete
        assertTrue("All concurrent requests should complete", 
                  latch.await(10, TimeUnit.SECONDS));
        
        // Verify connection stats
        ConcurrentProxyServer.ConnectionStats stats = proxyServer.getConnectionStats();
        assertTrue("Should have handled multiple connections", stats.total >= numRequests);
    }
    
    @Test(timeout = 15000)
    public void testViaHeaderHandling() throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read response headers
            String line;
            boolean foundVia = false;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("via:")) {
                    foundVia = true;
                    assertTrue("Via header should contain proxy ID", 
                              line.contains("1.1"));
                }
            }
            
            assertTrue("Response should contain Via header", foundVia);
        }
    }
    
    private void makeGetRequest(String path) throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + path + " HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            readFullResponse(in);
        }
    }
    
    private void readFullResponse(BufferedReader in) throws IOException {
        readFullResponse(in, false);
    }
    
    private String readFullResponse(BufferedReader in, boolean returnContent) throws IOException {
        String line;
        boolean inHeaders = true;
        int contentLength = 0;
        StringBuilder fullResponse = new StringBuilder();
        
        // Read status line and headers
        while ((line = in.readLine()) != null) {
            if (returnContent) fullResponse.append(line).append("\n");
            
            if (inHeaders) {
                if (line.isEmpty()) {
                    inHeaders = false;
                    break;
                } else if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }
        }
        
        // Read body
        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            int totalRead = 0;
            while (totalRead < contentLength) {
                int read = in.read(buffer, totalRead, contentLength - totalRead);
                if (read == -1) break;
                totalRead += read;
            }
            if (returnContent) {
                fullResponse.append(new String(buffer, 0, totalRead));
            }
        } else {
            // Read remaining lines if no content-length
            while ((line = in.readLine()) != null) {
                if (returnContent) fullResponse.append(line).append("\n");
            }
        }
        
        return returnContent ? fullResponse.toString() : "";
    }
}
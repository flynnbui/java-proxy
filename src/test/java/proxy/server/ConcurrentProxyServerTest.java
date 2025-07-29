package proxy.server;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import proxy.config.ProxyConfig;
import proxy.cache.HTTPCache;
import proxy.http.HTTPRequest;
import proxy.http.HTTPResponse;

import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unit tests for ConcurrentProxyServer class.
 * Tests concurrency, CONNECT method, caching, and persistence.
 */
public class ConcurrentProxyServerTest {
    private ProxyConfig config;
    private ConcurrentProxyServer server;
    private Thread serverThread;
    private int testPort;
    
    @Before
    public void setUp() throws IOException {
        // Find an available port for testing
        testPort = findAvailablePort();
        config = new ProxyConfig(testPort, 5, 1024, 4096);
        server = new ConcurrentProxyServer(config, 5);
    }
    
    @After
    public void tearDown() {
        if (server != null) {
            server.shutdown();
        }
        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                serverThread.interrupt();
            }
        }
    }
    
    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
    
    @Test
    public void testServerCreation() {
        assertNotNull("Server should be created", server);
        assertEquals("Should have correct config", config, server.config);
    }
    
    @Test
    public void testGetConnectionStats() {
        ConcurrentProxyServer.ConnectionStats stats = server.getConnectionStats();
        assertNotNull("Stats should not be null", stats);
        assertEquals("Initial total connections should be 0", 0, stats.total);
        assertEquals("Initial active connections should be 0", 0, stats.active);
        assertEquals("Initial completed connections should be 0", 0, stats.completed);
    }
    
    @Test
    public void testGetCacheStats() {
        HTTPCache.CacheStats stats = server.getCacheStats();
        assertNotNull("Cache stats should not be null", stats);
        assertEquals("Initial cache entries should be 0", 0, stats.entries);
        assertEquals("Initial cache size should be 0", 0, stats.size);
    }
    
    @Test
    public void testCacheOperations() {
        // Test that the cache is properly initialized and accessible
        HTTPCache.CacheStats initialStats = server.getCacheStats();
        assertEquals(0, initialStats.entries);
        assertEquals(0, initialStats.size);
        assertEquals(0, initialStats.hits);
        assertEquals(0, initialStats.misses);
    }
    
    @Test
    public void testShutdownWithoutStarting() {
        // Should be able to shutdown without issues even if not started
        server.shutdown();
        
        ConcurrentProxyServer.ConnectionStats stats = server.getConnectionStats();
        assertEquals(0, stats.total);
        assertEquals(0, stats.active);
        assertEquals(0, stats.completed);
    }
    
    @Test(timeout = 10000)
    public void testConcurrentConnectionHandling() throws Exception {
        // Start the server
        startServerInBackground();
        
        // Wait for server to start
        Thread.sleep(500);
        
        // Test multiple concurrent connections
        int numConnections = 3;
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < numConnections; i++) {
            final int connectionId = i;
            Thread clientThread = new Thread(() -> {
                try {
                    // Make a simple request that should result in an error (no real server)
                    Socket socket = new Socket("localhost", testPort);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
                    // Send a simple GET request
                    out.println("GET http://httpbin.org/get HTTP/1.1");
                    out.println("Host: httpbin.org");
                    out.println("Connection: close");
                    out.println();
                    
                    // Read response
                    String response = in.readLine();
                    if (response != null) {
                        // Should get some kind of response (likely an error due to network)
                        successCount.incrementAndGet();
                    }
                    
                    socket.close();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
            clientThread.start();
        }
        
        // Wait for all connections to complete
        assertTrue("All connections should complete within timeout", 
                  latch.await(8, TimeUnit.SECONDS));
        
        // Verify that the server handled multiple connections
        ConcurrentProxyServer.ConnectionStats stats = server.getConnectionStats();
        assertTrue("Should have handled multiple connections", stats.total >= numConnections);
    }
    
    @Test(timeout = 10000)
    public void testInvalidRequestHandling() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send malformed request
            out.println("INVALID REQUEST LINE");
            out.println();
            
            // Should get a 400 Bad Request response
            String response = in.readLine();
            assertNotNull("Should receive a response", response);
            assertTrue("Should be a 400 error", response.contains("400"));
        }
    }
    
    @Test(timeout = 10000)
    public void testConnectMethodInvalidPort() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send CONNECT request with invalid port (not 443)
            out.println("CONNECT example.com:80 HTTP/1.1");
            out.println("Host: example.com:80");
            out.println();
            
            // Should get a 400 Bad Request response
            String response = in.readLine();
            assertNotNull("Should receive a response", response);
            assertTrue("Should be a 400 error for non-443 port", response.contains("400"));
        }
    }
    
    @Test(timeout = 10000)
    public void testConnectMethodValidPort() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send CONNECT request with valid port 443
            out.println("CONNECT example.com:443 HTTP/1.1");
            out.println("Host: example.com:443");
            out.println();
            
            // Should get a response (likely error due to DNS/network, but valid format)
            String response = in.readLine();
            assertNotNull("Should receive a response", response);
            // Could be 200 (success) or 502 (connection failed) depending on network
        }
    }
    
    @Test(timeout = 10000)
    public void testSelfLoopDetection() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send request that would create a self-loop
            out.println("GET http://localhost:" + testPort + "/test HTTP/1.1");
            out.println("Host: localhost:" + testPort);
            out.println("Connection: close");
            out.println();
            
            // Should get a 421 Misdirected Request response
            String response = in.readLine();
            assertNotNull("Should receive a response", response);
            assertTrue("Should be a 421 error for self-loop", response.contains("421"));
        }
    }
    
    @Test(timeout = 10000)
    public void testPersistentConnection() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send first request with keep-alive
            out.println("GET http://httpbin.org/get HTTP/1.1");
            out.println("Host: httpbin.org");
            out.println("Connection: keep-alive");
            out.println();
            
            // Read first response
            String response1 = readHttpResponse(in);
            assertNotNull("Should receive first response", response1);
            
            // Send second request on same connection
            out.println("GET http://httpbin.org/headers HTTP/1.1");
            out.println("Host: httpbin.org");
            out.println("Connection: close"); // Close after second request
            out.println();
            
            // Read second response
            String response2 = readHttpResponse(in);
            assertNotNull("Should receive second response", response2);
        }
        
        // Verify connection stats show persistent behavior
        ConcurrentProxyServer.ConnectionStats stats = server.getConnectionStats();
        assertTrue("Should have handled at least one connection", stats.total >= 1);
    }
    
    @Test
    public void testUnsupportedMethod() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            // Send request with unsupported method
            out.println("DELETE http://example.com/resource HTTP/1.1");
            out.println("Host: example.com");
            out.println("Connection: close");
            out.println();
            
            // Should get a 400 Bad Request response
            String response = in.readLine();
            assertNotNull("Should receive a response", response);
            assertTrue("Should be a 400 error for unsupported method", response.contains("400"));
        }
    }
    
    @Test(timeout = 15000)
    public void testClientTimeout() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        try (Socket socket = new Socket("localhost", testPort)) {
            socket.setSoTimeout(10000); // 10 second timeout for test
            
            // Connect but don't send anything - should timeout
            Thread.sleep(6000); // Wait longer than proxy timeout (5 seconds)
            
            // Try to read - should get nothing or connection closed
            InputStream in = socket.getInputStream();
            int result = in.read();
            assertEquals("Connection should be closed", -1, result);
        } catch (IOException e) {
            // Expected - connection should be closed by proxy timeout
            assertTrue("Should be a connection-related error", 
                      e.getMessage().contains("Connection") || e.getMessage().contains("Socket"));
        }
    }
    
    private void startServerInBackground() {
        serverThread = new Thread(() -> {
            try {
                server.run();
            } catch (IOException e) {
                if (!e.getMessage().contains("Socket closed")) {
                    System.err.println("Server error: " + e.getMessage());
                }
            }
        });
        serverThread.start();
    }
    
    private String readHttpResponse(BufferedReader in) throws IOException {
        StringBuilder response = new StringBuilder();
        String line;
        boolean inHeaders = true;
        int contentLength = 0;
        
        // Read status line and headers
        while ((line = in.readLine()) != null) {
            response.append(line).append("\r\n");
            
            if (inHeaders) {
                if (line.isEmpty()) {
                    inHeaders = false;
                    break; // End of headers
                } else if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException e) {
                        // Ignore parsing errors
                    }
                }
            }
        }
        
        // Read body if present
        if (contentLength > 0) {
            char[] buffer = new char[contentLength];
            int bytesRead = in.read(buffer, 0, contentLength);
            if (bytesRead > 0) {
                response.append(new String(buffer, 0, bytesRead));
            }
        }
        
        return response.toString();
    }
    
    @Test
    public void testConnectionStatsUpdate() throws Exception {
        startServerInBackground();
        Thread.sleep(500);
        
        ConcurrentProxyServer.ConnectionStats initialStats = server.getConnectionStats();
        
        // Make a connection
        try (Socket socket = new Socket("localhost", testPort)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("GET http://httpbin.org/get HTTP/1.1");
            out.println("Host: httpbin.org");
            out.println("Connection: close");
            out.println();
            
            // Read response to complete the transaction
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            readHttpResponse(in);
        }
        
        // Wait a moment for stats to update
        Thread.sleep(100);
        
        ConcurrentProxyServer.ConnectionStats finalStats = server.getConnectionStats();
        assertTrue("Total connections should increase", finalStats.total > initialStats.total);
    }
}
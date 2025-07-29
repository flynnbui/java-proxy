package proxy;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import static org.junit.Assert.*;

import proxy.config.ProxyConfig;
import proxy.server.ConcurrentProxyServer;
import proxy.cache.HTTPCache;

import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stress tests for the HTTP Proxy implementation.
 * Tests high load scenarios, memory usage, and performance characteristics.
 */
public class ProxyStressTest {
    private ProxyConfig config;
    private ConcurrentProxyServer proxyServer;
    private Thread proxyThread;
    private int proxyPort;
    
    // Mock HTTP server for testing
    private ServerSocket mockServer;
    private Thread mockServerThread;
    private int mockServerPort;
    private volatile boolean mockServerRunning;
    
    @Before
    public void setUp() throws IOException {
        // Setup proxy with larger capacity for stress testing
        proxyPort = findAvailablePort();
        config = new ProxyConfig(proxyPort, 30, 4096, 16384);
        proxyServer = new ConcurrentProxyServer(config, 50); // More workers
        
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
        mockServerRunning = false;
        
        if (proxyServer != null) {
            proxyServer.shutdown();
        }
        if (proxyThread != null) {
            try {
                proxyThread.join(5000);
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
        mockServerRunning = true;
        
        mockServerThread = new Thread(() -> {
            while (mockServerRunning && !Thread.currentThread().isInterrupted() && !mockServer.isClosed()) {
                try {
                    Socket clientSocket = mockServer.accept();
                    // Handle each request in a separate thread for stress testing
                    Thread handler = new Thread(() -> handleMockRequest(clientSocket));
                    handler.start();
                } catch (IOException e) {
                    if (mockServerRunning && !e.getMessage().contains("Socket closed")) {
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
            
            // Read request line
            String requestLine = in.readLine();
            if (requestLine == null) return;
            
            // Read headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Process headers
            }
            
            // Send simple response
            String responseBody = "Response for: " + requestLine;
            out.println("HTTP/1.1 200 OK");
            out.println("Content-Type: text/plain");
            out.println("Content-Length: " + responseBody.length());
            out.println("Connection: close");
            out.println();
            out.print(responseBody);
            out.flush();
            
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
    
    @Test(timeout = 60000)
    public void testHighConcurrentLoad() throws Exception {
        int numThreads = 50;
        int requestsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        // Launch concurrent requests
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        try {
                            makeRequest(threadId + "-" + j);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            System.err.println("Request failed: " + e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Wait for completion
        assertTrue("All requests should complete within timeout", 
                  latch.await(45, TimeUnit.SECONDS));
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        executor.shutdown();
        
        // Verify results
        int totalRequests = numThreads * requestsPerThread;
        int successRate = (successCount.get() * 100) / totalRequests;
        
        System.out.println("Stress test results:");
        System.out.println("Total requests: " + totalRequests);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Errors: " + errorCount.get());
        System.out.println("Success rate: " + successRate + "%");
        System.out.println("Duration: " + duration + "ms");
        System.out.println("Requests/sec: " + (totalRequests * 1000.0 / duration));
        
        // Assert minimum success rate
        assertTrue("Success rate should be at least 80%", successRate >= 80);
        
        // Verify proxy statistics
        ConcurrentProxyServer.ConnectionStats stats = proxyServer.getConnectionStats();
        assertTrue("Should have handled many connections", stats.total >= totalRequests);
        
        System.out.println("Connection stats: " + stats.total + " total, " + 
                          stats.completed + " completed, " + stats.active + " active");
    }
    
    @Test(timeout = 30000)
    public void testCachePerformanceUnderLoad() throws Exception {
        int numThreads = 20;
        int requestsPerThread = 5;
        String commonUrl = "/cacheable-resource";
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger cacheHits = new AtomicInteger(0);
        
        // Make initial request to populate cache
        makeSpecificRequest(commonUrl);
        
        HTTPCache.CacheStats initialStats = proxyServer.getCacheStats();
        
        // Launch concurrent requests to same resource
        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        makeSpecificRequest(commonUrl);
                    }
                } catch (Exception e) {
                    System.err.println("Cache test request failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("Cache performance test should complete", 
                  latch.await(25, TimeUnit.SECONDS));
        
        executor.shutdown();
        
        HTTPCache.CacheStats finalStats = proxyServer.getCacheStats();
        
        System.out.println("Cache performance test results:");
        System.out.println("Initial cache hits: " + initialStats.hits);
        System.out.println("Final cache hits: " + finalStats.hits);
        System.out.println("Cache hit rate: " + (finalStats.hitRate * 100) + "%");
        
        // Should have significant cache hits
        assertTrue("Should have cache hits from concurrent access", 
                  finalStats.hits > initialStats.hits);
    }
    
    @Test(timeout = 20000)
    public void testMemoryUsageUnderLoad() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Generate load
        int numRequests = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(numRequests);
        
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    makeRequest("memory-test-" + requestId);
                } catch (Exception e) {
                    // Continue on errors
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Force garbage collection and measure memory
        System.gc();
        Thread.sleep(1000);
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long memoryIncrease = finalMemory - initialMemory;
        long memoryIncreaseMB = memoryIncrease / (1024 * 1024);
        
        System.out.println("Memory usage test:");
        System.out.println("Initial memory: " + (initialMemory / (1024 * 1024)) + " MB");
        System.out.println("Final memory: " + (finalMemory / (1024 * 1024)) + " MB");
        System.out.println("Memory increase: " + memoryIncreaseMB + " MB");
        
        // Memory increase should be reasonable (less than 100MB for this test)
        assertTrue("Memory increase should be reasonable", memoryIncreaseMB < 100);
    }
    
    @Test(timeout = 30000)
    public void testConnectionPoolExhaustion() throws Exception {
        // Try to overwhelm the connection pool
        int numConnections = 100; // More than max workers (50)
        ExecutorService executor = Executors.newFixedThreadPool(numConnections);
        CountDownLatch latch = new CountDownLatch(numConnections);
        AtomicInteger completedRequests = new AtomicInteger(0);
        
        for (int i = 0; i < numConnections; i++) {
            final int connectionId = i;
            executor.submit(() -> {
                try {
                    // Use persistent connections to hold workers
                    makeSlowRequest("slow-" + connectionId);
                    completedRequests.incrementAndGet();
                } catch (Exception e) {
                    // Some connections may fail due to resource limits
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("Connection pool test should complete", 
                  latch.await(25, TimeUnit.SECONDS));
        
        executor.shutdown();
        
        // Should handle a reasonable number of connections
        int completed = completedRequests.get();
        System.out.println("Connection pool test: " + completed + "/" + numConnections + " completed");
        
        assertTrue("Should complete most connections", completed >= numConnections * 0.7);
    }
    
    @Test(timeout = 15000)
    @Ignore("This test may be flaky in CI environments")
    public void testResponseTimeUnderLoad() throws Exception {
        // Measure response times under concurrent load
        int numRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(numRequests);
        ConcurrentLinkedQueue<Long> responseTimes = new ConcurrentLinkedQueue<>();
        
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    long startTime = System.currentTimeMillis();
                    makeRequest("perf-test-" + requestId);
                    long endTime = System.currentTimeMillis();
                    responseTimes.offer(endTime - startTime);
                } catch (Exception e) {
                    // Continue on errors
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Calculate statistics
        long totalTime = responseTimes.stream().mapToLong(Long::longValue).sum();
        long avgResponseTime = totalTime / responseTimes.size();
        long maxResponseTime = responseTimes.stream().mapToLong(Long::longValue).max().orElse(0);
        
        System.out.println("Response time test:");
        System.out.println("Requests processed: " + responseTimes.size());
        System.out.println("Average response time: " + avgResponseTime + "ms");
        System.out.println("Max response time: " + maxResponseTime + "ms");
        
        // Response times should be reasonable
        assertTrue("Average response time should be reasonable", avgResponseTime < 5000);
        assertTrue("Max response time should be reasonable", maxResponseTime < 10000);
    }
    
    private void makeRequest(String identifier) throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            socket.setSoTimeout(10000); // 10 second timeout
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + "/test/" + identifier + " HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read response
            String statusLine = in.readLine();
            if (statusLine == null || !statusLine.contains("200")) {
                throw new IOException("Invalid response: " + statusLine);
            }
            
            // Read headers
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                // Process headers
            }
            
            // Read body
            while ((line = in.readLine()) != null) {
                // Process body
            }
        }
    }
    
    private void makeSpecificRequest(String path) throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            socket.setSoTimeout(5000);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + path + " HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: close");
            out.println();
            
            // Read full response
            String line;
            while ((line = in.readLine()) != null) {
                // Process response
            }
        }
    }
    
    private void makeSlowRequest(String identifier) throws Exception {
        try (Socket socket = new Socket("localhost", proxyPort)) {
            socket.setSoTimeout(15000);
            
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            out.println("GET http://localhost:" + mockServerPort + "/slow/" + identifier + " HTTP/1.1");
            out.println("Host: localhost:" + mockServerPort);
            out.println("Connection: keep-alive"); // Use keep-alive to hold connection
            out.println();
            
            // Read response slowly
            String statusLine = in.readLine();
            if (statusLine != null && statusLine.contains("200")) {
                // Read headers
                String line;
                while ((line = in.readLine()) != null && !line.isEmpty()) {
                    Thread.sleep(10); // Slow down reading
                }
                
                // Read body
                while ((line = in.readLine()) != null) {
                    Thread.sleep(10); // Slow down reading
                }
            }
        }
    }
}
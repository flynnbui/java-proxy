package proxy.logging;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Unit tests for ProxyLogger class.
 */
public class ProxyLoggerTest {
    private ByteArrayOutputStream outputStream;
    private ByteArrayOutputStream errorStream;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ProxyLogger logger;
    
    @Before
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        errorStream = new ByteArrayOutputStream();
        originalOut = System.out;
        originalErr = System.err;
        
        // Redirect System.err for warning/error tests
        System.setErr(new PrintStream(errorStream));
        
        logger = new ProxyLogger(new PrintStream(outputStream));
    }
    
    @org.junit.After
    public void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }
    
    @Test
    public void testDefaultConstructor() {
        ProxyLogger defaultLogger = new ProxyLogger();
        assertNotNull("Default logger should be created", defaultLogger);
        
        // Test that it doesn't throw exceptions
        defaultLogger.logBasicTransaction("127.0.0.1", 1234, "GET / HTTP/1.1", 200, 100);
    }
    
    @Test
    public void testNullOutputStreamHandling() {
        ProxyLogger nullLogger = new ProxyLogger(null);
        assertNotNull("Logger with null stream should be created", nullLogger);
        
        // Should use System.out as fallback
        nullLogger.logBasicTransaction("127.0.0.1", 1234, "GET / HTTP/1.1", 200, 100);
    }
    
    @Test
    public void testBasicLogTransaction() {
        logger.logTransaction("127.0.0.1", 56789, "H", "GET http://example.com/ HTTP/1.1", 200, 1024);
        
        String logOutput = outputStream.toString();
        assertFalse("Should have log output", logOutput.isEmpty());
        
        // Check components are present
        assertTrue("Should contain client IP", logOutput.contains("127.0.0.1"));
        assertTrue("Should contain client port", logOutput.contains("56789"));
        assertTrue("Should contain cache status", logOutput.contains("H"));
        assertTrue("Should contain request line", logOutput.contains("GET http://example.com/ HTTP/1.1"));
        assertTrue("Should contain status code", logOutput.contains("200"));
        assertTrue("Should contain response bytes", logOutput.contains("1024"));
        
        // Check timestamp format [dd/MMM/yyyy:HH:mm:ss Z]
        assertTrue("Should contain properly formatted timestamp", 
                  logOutput.matches(".*\\[\\d{2}/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [+-]\\d{4}\\].*"));
    }
    
    @Test
    public void testLogTransactionFormat() {
        logger.logTransaction("192.168.1.100", 12345, "M", "POST /api HTTP/1.1", 201, 512);
        
        String logOutput = outputStream.toString().trim();
        
        // Should follow format: host port cache date request status bytes
        String[] parts = logOutput.split(" ");
        assertTrue("Should have enough parts", parts.length >= 7);
        
        assertEquals("Host should be first", "192.168.1.100", parts[0]);
        assertEquals("Port should be second", "12345", parts[1]);
        assertEquals("Cache status should be third", "M", parts[2]);
        assertTrue("Date should be in brackets", parts[3].startsWith("["));
        assertTrue("Request should be quoted", logOutput.contains("\"POST /api HTTP/1.1\""));
        assertTrue("Should end with status and bytes", logOutput.endsWith("201 512"));
    }
    
    @Test
    public void testNullCacheStatusDefault() {
        logger.logTransaction("127.0.0.1", 8080, null, "GET /test HTTP/1.1", 404, 256);
        
        String logOutput = outputStream.toString();
        assertTrue("Should use default '-' for null cache status", logOutput.contains("127.0.0.1 8080 -"));
    }
    
    @Test
    public void testLogBasicTransaction() {
        logger.logBasicTransaction("10.0.0.1", 9999, "HEAD /resource HTTP/1.1", 200, 0);
        
        String logOutput = outputStream.toString();
        assertTrue("Should contain IP", logOutput.contains("10.0.0.1"));
        assertTrue("Should contain port", logOutput.contains("9999"));
        assertTrue("Should use default cache status", logOutput.contains("10.0.0.1 9999 -"));
        assertTrue("Should contain request", logOutput.contains("HEAD /resource HTTP/1.1"));
        assertTrue("Should contain status", logOutput.contains("200"));
        assertTrue("Should contain zero bytes", logOutput.contains("0"));
    }
    
    @Test
    public void testLogCacheTransactionHit() {
        logger.logCacheTransaction("127.0.0.1", 3128, true, "GET /cached HTTP/1.1", 200, 2048);
        
        String logOutput = outputStream.toString();
        assertTrue("Should use 'H' for cache hit", logOutput.contains("127.0.0.1 3128 H"));
        assertTrue("Should contain request", logOutput.contains("GET /cached HTTP/1.1"));
    }
    
    @Test
    public void testLogCacheTransactionMiss() {
        logger.logCacheTransaction("127.0.0.1", 3128, false, "GET /uncached HTTP/1.1", 200, 1536);
        
        String logOutput = outputStream.toString();
        assertTrue("Should use 'M' for cache miss", logOutput.contains("127.0.0.1 3128 M"));
        assertTrue("Should contain request", logOutput.contains("GET /uncached HTTP/1.1"));
    }
    
    @Test
    public void testLogWarning() {
        logger.logWarning("Test warning message");
        
        String errorOutput = errorStream.toString();
        assertTrue("Should contain warning prefix", errorOutput.contains("[WARN]"));
        assertTrue("Should contain message", errorOutput.contains("Test warning message"));
        assertTrue("Should contain timestamp", 
                  errorOutput.matches(".*\\[\\d{2}/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [+-]\\d{4}\\].*"));
    }
    
    @Test
    public void testLogErrorWithoutException() {
        logger.logError("Test error message", null);
        
        String errorOutput = errorStream.toString();
        assertTrue("Should contain error prefix", errorOutput.contains("[ERROR]"));
        assertTrue("Should contain message", errorOutput.contains("Test error message"));
        assertTrue("Should contain timestamp", 
                  errorOutput.matches(".*\\[\\d{2}/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [+-]\\d{4}\\].*"));
    }
    
    @Test
    public void testLogErrorWithException() {
        Exception testException = new RuntimeException("Test exception");
        logger.logError("Error with exception", testException);
        
        String errorOutput = errorStream.toString();
        assertTrue("Should contain error prefix", errorOutput.contains("[ERROR]"));
        assertTrue("Should contain message", errorOutput.contains("Error with exception"));
        assertTrue("Should contain exception info", errorOutput.contains("RuntimeException"));
        assertTrue("Should contain exception message", errorOutput.contains("Test exception"));
        assertTrue("Should contain stack trace", errorOutput.contains("at "));
    }
    
    @Test
    public void testSpecialCharactersInRequestLine() {
        String requestWithSpecialChars = "GET /path?param=value&other=test%20data HTTP/1.1";
        logger.logTransaction("127.0.0.1", 8080, "-", requestWithSpecialChars, 200, 100);
        
        String logOutput = outputStream.toString();
        assertTrue("Should preserve special characters", logOutput.contains(requestWithSpecialChars));
        assertTrue("Should quote request line", logOutput.contains("\"" + requestWithSpecialChars + "\""));
    }
    
    @Test
    public void testLargeNumbers() {
        logger.logTransaction("255.255.255.255", 65535, "-", "GET /large HTTP/1.1", 200, 999999999);
        
        String logOutput = outputStream.toString();
        assertTrue("Should handle large port number", logOutput.contains("65535"));
        assertTrue("Should handle large byte count", logOutput.contains("999999999"));
    }
    
    @Test
    public void testErrorStatusCodes() {
        int[] errorCodes = {400, 404, 500, 502, 504};
        
        for (int code : errorCodes) {
            ByteArrayOutputStream testOutput = new ByteArrayOutputStream();
            ProxyLogger testLogger = new ProxyLogger(new PrintStream(testOutput));
            
            testLogger.logTransaction("127.0.0.1", 8080, "-", "GET /error HTTP/1.1", code, 0);
            
            String logOutput = testOutput.toString();
            assertTrue("Should contain error code " + code, logOutput.contains(String.valueOf(code)));
        }
    }
    
    @Test
    public void testEmptyRequestLine() {
        logger.logTransaction("127.0.0.1", 8080, "-", "", 400, 0);
        
        String logOutput = outputStream.toString();
        assertTrue("Should handle empty request line", logOutput.contains("\"\""));
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        int numThreads = 10;
        int logsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < logsPerThread; j++) {
                        logger.logTransaction("127.0.0." + threadId, 8000 + j, "T", 
                                            "GET /thread" + threadId + "/" + j + " HTTP/1.1", 200, j * 10);
                        
                        // Mix in some warnings and errors
                        if (j % 10 == 0) {
                            logger.logWarning("Thread " + threadId + " warning " + j);
                        }
                        if (j % 20 == 0) {
                            logger.logError("Thread " + threadId + " error " + j, null);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue("All threads should complete", latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        String logOutput = outputStream.toString();
        String errorOutput = errorStream.toString();
        
        // Count total log entries
        int logLines = logOutput.split("\n").length;
        int expectedLogs = numThreads * logsPerThread;
        
        assertEquals("Should have correct number of log entries", expectedLogs, logLines - 1); // -1 for last empty line
        
        // Check that all thread IDs appear
        for (int i = 0; i < numThreads; i++) {
            assertTrue("Should contain logs from thread " + i, 
                      logOutput.contains("127.0.0." + i));
        }
        
        // Check warning and error logs
        assertTrue("Should have warning logs", errorOutput.contains("[WARN]"));
        assertTrue("Should have error logs", errorOutput.contains("[ERROR]"));
    }
    
    @Test
    public void testTimestampFormat() {
        logger.logTransaction("127.0.0.1", 8080, "-", "GET / HTTP/1.1", 200, 100);
        
        String logOutput = outputStream.toString();
        
        // Extract timestamp from log
        Pattern timestampPattern = Pattern.compile("\\[(\\d{2}/\\w{3}/\\d{4}:\\d{2}:\\d{2}:\\d{2} [+-]\\d{4})\\]");
        assertTrue("Should match timestamp format", timestampPattern.matcher(logOutput).find());
    }
    
    @Test
    public void testIPv6Address() {
        logger.logTransaction("::1", 8080, "-", "GET / HTTP/1.1", 200, 100);
        
        String logOutput = outputStream.toString();
        assertTrue("Should handle IPv6 address", logOutput.contains("::1"));
    }
    
    @Test
    public void testZeroValues() {
        logger.logTransaction("0.0.0.0", 0, "0", "0", 0, 0);
        
        String logOutput = outputStream.toString();
        assertTrue("Should handle zero values", logOutput.contains("0.0.0.0 0 0"));
        assertTrue("Should contain zero status and bytes", logOutput.contains("0 0"));
    }
}
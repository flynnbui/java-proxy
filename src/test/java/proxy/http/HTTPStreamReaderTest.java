package proxy.http;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Unit tests for HTTPStreamReader class.
 * Tests reading HTTP messages from network streams.
 */
public class HTTPStreamReaderTest {
    
    @Test(timeout = 5000)
    public void testReadSimpleHttpRequest() throws Exception {
        String rawRequest = "GET http://example.com/path HTTP/1.1\r\n" +
                           "Host: example.com\r\n" +
                           "User-Agent: TestAgent/1.0\r\n" +
                           "\r\n";
        
        // Create mock socket connection
        try (MockSocketConnection connection = new MockSocketConnection(rawRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPRequest request = reader.readHttpRequest();
            
            assertEquals("GET", request.getMethod());
            assertEquals("http://example.com/path", request.getTarget());
            assertEquals("HTTP/1.1", request.getVersion());
            assertEquals("example.com", request.getHeader("Host"));
            assertEquals("TestAgent/1.0", request.getHeader("User-Agent"));
        }
    }
    
    @Test(timeout = 5000)
    public void testReadHttpRequestWithBody() throws Exception {
        String requestBody = "{\"test\": \"data\"}";
        String rawRequest = "POST http://api.example.com/data HTTP/1.1\r\n" +
                           "Host: api.example.com\r\n" +
                           "Content-Type: application/json\r\n" +
                           "Content-Length: " + requestBody.length() + "\r\n" +
                           "\r\n" +
                           requestBody;
        
        try (MockSocketConnection connection = new MockSocketConnection(rawRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPRequest request = reader.readHttpRequest();
            
            assertEquals("POST", request.getMethod());
            assertTrue(request.hasBody());
            assertEquals(requestBody, new String(request.getBody()));
        }
    }
    
    @Test(timeout = 5000)
    public void testReadHttpResponse() throws Exception {
        String responseBody = "<html><body>Hello World</body></html>";
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + responseBody.length() + "\r\n" +
                            "Server: TestServer/1.0\r\n" +
                            "\r\n" +
                            responseBody;
        
        try (MockSocketConnection connection = new MockSocketConnection(rawResponse)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPResponse response = reader.readHttpResponse("GET");
            
            assertEquals("HTTP/1.1", response.getVersion());
            assertEquals(200, response.getStatusCode());
            assertEquals("OK", response.getReasonPhrase());
            assertEquals("text/html", response.getHeader("Content-Type"));
            assertEquals(responseBody, new String(response.getBody()));
        }
    }
    
    @Test(timeout = 5000)
    public void testReadHeadResponse() throws Exception {
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: 1024\r\n" +
                            "\r\n";
        
        try (MockSocketConnection connection = new MockSocketConnection(rawResponse)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPResponse response = reader.readHttpResponse("HEAD");
            
            assertEquals(200, response.getStatusCode());
            assertEquals("1024", response.getHeader("Content-Length"));
            assertFalse(response.hasBody("HEAD"));
            assertEquals(0, response.getBody().length);
        }
    }
    
    @Test(timeout = 5000)
    public void testReadChunkedResponse() throws Exception {
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "\r\n" +
                            "5\r\nHello\r\n" +
                            "6\r\n World\r\n" +
                            "0\r\n\r\n";
        
        try (MockSocketConnection connection = new MockSocketConnection(rawResponse)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPResponse response = reader.readHttpResponse("GET");
            
            assertEquals(200, response.getStatusCode());
            assertTrue(response.hasTransferEncoding());
            assertEquals("Hello World", new String(response.getBody()));
        }
    }
    
    @Test(timeout = 10000, expected = HTTPParseException.class)
    public void testReadTimeout() throws Exception {
        // Create a connection that never sends data
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            Thread serverThread = new Thread(() -> {
                try {
                    Socket clientSocket = serverSocket.accept();
                    // Don't send any data - should cause timeout
                    Thread.sleep(10000);
                    clientSocket.close();
                } catch (Exception e) {
                    // Expected for timeout test
                }
            });
            serverThread.start();
            
            Socket socket = new Socket("localhost", serverSocket.getLocalPort());
            HTTPStreamReader reader = new HTTPStreamReader(socket, 1); // 1 second timeout
            reader.readHttpRequest(); // Should timeout
        }
    }
    
    @Test(timeout = 5000)
    public void testReadConnectRequest() throws Exception {
        String rawRequest = "CONNECT example.com:443 HTTP/1.1\r\n" +
                           "Host: example.com:443\r\n" +
                           "\r\n";
        
        try (MockSocketConnection connection = new MockSocketConnection(rawRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPRequest request = reader.readHttpRequest();
            
            assertEquals("CONNECT", request.getMethod());
            assertEquals("example.com:443", request.getTarget());
            assertFalse(request.hasBody());
        }
    }
    
    @Test(timeout = 5000)
    public void testReadResponseWithoutContentLength() throws Exception {
        String responseBody = "Response without content-length";
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Connection: close\r\n" +
                            "\r\n" +
                            responseBody;
        
        try (MockSocketConnection connection = new MockSocketConnection(rawResponse)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            HTTPResponse response = reader.readHttpResponse("GET");
            
            assertEquals(200, response.getStatusCode());
            // Should read until connection closes
            assertTrue(response.getBody().length > 0);
        }
    }
    
    @Test(timeout = 5000)
    public void testReadLargeRequest() throws Exception {
        String largeBody = "x".repeat(10000); // 10KB
        String rawRequest = "POST http://example.com/upload HTTP/1.1\r\n" +
                           "Content-Type: text/plain\r\n" +
                           "Content-Length: " + largeBody.length() + "\r\n" +
                           "\r\n" +
                           largeBody;
        
        try (MockSocketConnection connection = new MockSocketConnection(rawRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 10);
            HTTPRequest request = reader.readHttpRequest();
            
            assertEquals("POST", request.getMethod());
            assertEquals(largeBody.length(), request.getBody().length);
            assertEquals(largeBody, new String(request.getBody()));
        }
    }
    
    @Test(timeout = 5000, expected = HTTPParseException.class)
    public void testReadMalformedRequest() throws Exception {
        String malformedRequest = "INVALID REQUEST\r\n\r\n";
        
        try (MockSocketConnection connection = new MockSocketConnection(malformedRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            reader.readHttpRequest();
        }
    }
    
    @Test(timeout = 5000, expected = HTTPParseException.class)
    public void testReadIncompleteRequest() throws Exception {
        // Request without headers end marker
        String incompleteRequest = "GET http://example.com/ HTTP/1.1\r\n" +
                                  "Host: example.com\r\n";
        // Missing final \r\n
        
        try (MockSocketConnection connection = new MockSocketConnection(incompleteRequest)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 2);
            reader.readHttpRequest();
        }
    }
    
    @Test(timeout = 5000)
    public void testReadMultipleRequests() throws Exception {
        String request1 = "GET http://example.com/1 HTTP/1.1\r\n" +
                         "Host: example.com\r\n" +
                         "Connection: keep-alive\r\n" +
                         "\r\n";
        
        String request2 = "GET http://example.com/2 HTTP/1.1\r\n" +
                         "Host: example.com\r\n" +
                         "Connection: close\r\n" +
                         "\r\n";
        
        try (MockSocketConnection connection = new MockSocketConnection(request1 + request2)) {
            HTTPStreamReader reader = new HTTPStreamReader(connection.getClientSocket(), 5);
            
            // Read first request
            HTTPRequest req1 = reader.readHttpRequest();
            assertEquals("http://example.com/1", req1.getTarget());
            assertEquals("keep-alive", req1.getHeader("Connection"));
            
            // Read second request
            HTTPRequest req2 = reader.readHttpRequest();
            assertEquals("http://example.com/2", req2.getTarget());
            assertEquals("close", req2.getHeader("Connection"));
        }
    }
    
    /**
     * Helper class to create mock socket connections for testing.
     */
    private static class MockSocketConnection implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Socket clientSocket;
        private final Thread serverThread;
        
        public MockSocketConnection(String data) throws IOException {
            serverSocket = new ServerSocket(0);
            
            serverThread = new Thread(() -> {
                try (Socket serverSideSocket = serverSocket.accept();
                     OutputStream out = serverSideSocket.getOutputStream()) {
                    
                    out.write(data.getBytes());
                    out.flush();
                    
                    // Keep connection open briefly
                    Thread.sleep(100);
                    
                } catch (Exception e) {
                    // Expected for test scenarios
                }
            });
            serverThread.start();
            
            clientSocket = new Socket("localhost", serverSocket.getLocalPort());
        }
        
        public Socket getClientSocket() {
            return clientSocket;
        }
        
        @Override
        public void close() throws IOException {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (serverThread != null) {
                serverThread.interrupt();
            }
        }
    }
}
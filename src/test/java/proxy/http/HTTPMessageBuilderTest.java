package proxy.http;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests for HTTPMessageBuilder class.
 * Tests building HTTP messages from components.
 */
public class HTTPMessageBuilderTest {
    
    @Test
    public void testBuildSimpleGetRequest() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        headers.put("User-Agent", "TestAgent/1.0");
        headers.put("Connection", "close");
        
        byte[] request = HTTPMessageBuilder.buildRequest("GET", "/path", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertTrue("Should start with request line", requestStr.startsWith("GET /path HTTP/1.1\r\n"));
        assertTrue("Should contain Host header", requestStr.contains("Host: example.com\r\n"));
        assertTrue("Should contain User-Agent header", requestStr.contains("User-Agent: TestAgent/1.0\r\n"));
        assertTrue("Should contain Connection header", requestStr.contains("Connection: close\r\n"));
        assertTrue("Should end headers with CRLF", requestStr.contains("\r\n\r\n"));
        assertFalse("Should not have body", requestStr.endsWith("body"));
    }
    
    @Test
    public void testBuildPostRequestWithBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "api.example.com");
        headers.put("Content-Type", "application/json");
        headers.put("Content-Length", "17");
        
        byte[] body = "{\"test\": \"data\"}".getBytes();
        byte[] request = HTTPMessageBuilder.buildRequest("POST", "/api/data", "HTTP/1.1", headers, body);
        String requestStr = new String(request);
        
        assertTrue("Should start with POST request line", requestStr.startsWith("POST /api/data HTTP/1.1\r\n"));
        assertTrue("Should contain Content-Type header", requestStr.contains("Content-Type: application/json\r\n"));
        assertTrue("Should contain Content-Length header", requestStr.contains("Content-Length: 17\r\n"));
        assertTrue("Should have headers separator", requestStr.contains("\r\n\r\n"));
        assertTrue("Should end with body", requestStr.endsWith("{\"test\": \"data\"}"));
    }
    
    @Test
    public void testBuildConnectRequest() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com:443");
        
        byte[] request = HTTPMessageBuilder.buildRequest("CONNECT", "example.com:443", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertTrue("Should start with CONNECT request line", 
                  requestStr.startsWith("CONNECT example.com:443 HTTP/1.1\r\n"));
        assertTrue("Should contain Host header", requestStr.contains("Host: example.com:443\r\n"));
        assertTrue("Should end headers properly", requestStr.endsWith("\r\n\r\n"));
    }
    
    @Test
    public void testBuildRequestWithEmptyHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        
        byte[] request = HTTPMessageBuilder.buildRequest("GET", "/", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertEquals("Should only contain request line and headers separator", 
                    "GET / HTTP/1.1\r\n\r\n", requestStr);
    }
    
    @Test
    public void testBuildRequestWithNullHeaders() {
        byte[] request = HTTPMessageBuilder.buildRequest("HEAD", "/resource", "HTTP/1.1", null, null);
        String requestStr = new String(request);
        
        assertEquals("Should handle null headers", "HEAD /resource HTTP/1.1\r\n\r\n", requestStr);
    }
    
    @Test
    public void testBuildRequestWithNullBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        
        byte[] request = HTTPMessageBuilder.buildRequest("GET", "/test", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertTrue("Should contain request line", requestStr.contains("GET /test HTTP/1.1\r\n"));
        assertTrue("Should contain Host header", requestStr.contains("Host: example.com\r\n"));
        assertTrue("Should end with headers separator", requestStr.endsWith("\r\n\r\n"));
    }
    
    @Test
    public void testBuildResponse200() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/html");
        headers.put("Content-Length", "27");
        headers.put("Server", "TestServer/1.0");
        
        byte[] body = "<html><body>Hi</body></html>".getBytes();
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 200, "OK", headers, body);
        String responseStr = new String(response);
        
        assertTrue("Should start with status line", responseStr.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue("Should contain Content-Type header", responseStr.contains("Content-Type: text/html\r\n"));
        assertTrue("Should contain Content-Length header", responseStr.contains("Content-Length: 27\r\n"));
        assertTrue("Should contain Server header", responseStr.contains("Server: TestServer/1.0\r\n"));
        assertTrue("Should have headers separator", responseStr.contains("\r\n\r\n"));
        assertTrue("Should end with body", responseStr.endsWith("<html><body>Hi</body></html>"));
    }
    
    @Test
    public void testBuildResponse404() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain");
        headers.put("Content-Length", "9");
        
        byte[] body = "Not Found".getBytes();
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 404, "Not Found", headers, body);
        String responseStr = new String(response);
        
        assertTrue("Should start with 404 status line", responseStr.startsWith("HTTP/1.1 404 Not Found\r\n"));
        assertTrue("Should contain error message", responseStr.endsWith("Not Found"));
    }
    
    @Test
    public void testBuildResponseWithoutReasonPhrase() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Length", "0");
        
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 204, "", headers, null);
        String responseStr = new String(response);
        
        assertTrue("Should handle empty reason phrase", responseStr.startsWith("HTTP/1.1 204 \r\n"));
    }
    
    @Test
    public void testBuildResponseWithNullReasonPhrase() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Length", "0");
        
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 304, null, headers, null);
        String responseStr = new String(response);
        
        assertTrue("Should handle null reason phrase", responseStr.startsWith("HTTP/1.1 304 \r\n"));
    }
    
    @Test
    public void testBuildHeadResponse() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/html");
        headers.put("Content-Length", "1024");
        
        // HEAD responses have headers but no body
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 200, "OK", headers, null);
        String responseStr = new String(response);
        
        assertTrue("Should contain status line", responseStr.contains("HTTP/1.1 200 OK\r\n"));
        assertTrue("Should contain Content-Length", responseStr.contains("Content-Length: 1024\r\n"));
        assertTrue("Should end with headers separator", responseStr.endsWith("\r\n\r\n"));
    }
    
    @Test
    public void testBuildResponseWithCustomHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Custom-Header", "custom-value");
        headers.put("Cache-Control", "no-cache");
        headers.put("Access-Control-Allow-Origin", "*");
        
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 200, "OK", headers, null);
        String responseStr = new String(response);
        
        assertTrue("Should contain custom header", responseStr.contains("X-Custom-Header: custom-value\r\n"));
        assertTrue("Should contain Cache-Control", responseStr.contains("Cache-Control: no-cache\r\n"));
        assertTrue("Should contain CORS header", responseStr.contains("Access-Control-Allow-Origin: *\r\n"));
    }
    
    @Test
    public void testBuildRequestWithSpecialCharacters() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        headers.put("User-Agent", "TestAgent/1.0 (special;chars)");
        
        String specialPath = "/path?param=value%20with%20spaces&other=test";
        byte[] request = HTTPMessageBuilder.buildRequest("GET", specialPath, "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertTrue("Should preserve special characters in path", 
                  requestStr.contains("GET " + specialPath + " HTTP/1.1\r\n"));
        assertTrue("Should preserve special characters in headers", 
                  requestStr.contains("User-Agent: TestAgent/1.0 (special;chars)\r\n"));
    }
    
    @Test
    public void testBuildResponseWithLargeBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain");
        
        String largeBodyContent = "x".repeat(10000); // 10KB
        headers.put("Content-Length", String.valueOf(largeBodyContent.length()));
        
        byte[] body = largeBodyContent.getBytes();
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 200, "OK", headers, body);
        String responseStr = new String(response);
        
        assertTrue("Should contain status line", responseStr.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue("Should contain Content-Length", 
                  responseStr.contains("Content-Length: " + largeBodyContent.length() + "\r\n"));
        assertTrue("Should end with large body", responseStr.endsWith(largeBodyContent));
    }
    
    @Test
    public void testBuildRequestWithZeroLengthBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        headers.put("Content-Length", "0");
        
        byte[] emptyBody = new byte[0];
        byte[] request = HTTPMessageBuilder.buildRequest("POST", "/empty", "HTTP/1.1", headers, emptyBody);
        String requestStr = new String(request);
        
        assertTrue("Should contain POST request line", requestStr.contains("POST /empty HTTP/1.1\r\n"));
        assertTrue("Should contain Content-Length: 0", requestStr.contains("Content-Length: 0\r\n"));
        assertTrue("Should end with headers separator", requestStr.endsWith("\r\n\r\n"));
    }
    
    @Test
    public void testHeaderOrdering() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        headers.put("User-Agent", "TestAgent");
        headers.put("Accept", "*/*");
        headers.put("Connection", "close");
        
        byte[] request = HTTPMessageBuilder.buildRequest("GET", "/", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        // Headers should appear in the order they were added (LinkedHashMap preserves order)
        int hostIndex = requestStr.indexOf("Host:");
        int userAgentIndex = requestStr.indexOf("User-Agent:");
        int acceptIndex = requestStr.indexOf("Accept:");
        int connectionIndex = requestStr.indexOf("Connection:");
        
        assertTrue("Headers should be in correct order", 
                  hostIndex < userAgentIndex && userAgentIndex < acceptIndex && acceptIndex < connectionIndex);
    }
    
    @Test
    public void testUnicodeInHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Host", "example.com");
        headers.put("X-Unicode-Header", "测试中文");
        
        byte[] request = HTTPMessageBuilder.buildRequest("GET", "/unicode", "HTTP/1.1", headers, null);
        String requestStr = new String(request);
        
        assertTrue("Should handle Unicode in headers", requestStr.contains("X-Unicode-Header: 测试中文\r\n"));
    }
    
    @Test
    public void testUnicodeInBody() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "text/plain; charset=UTF-8");
        
        String unicodeBody = "Hello 世界! 🌍";
        byte[] bodyBytes = unicodeBody.getBytes();
        headers.put("Content-Length", String.valueOf(bodyBytes.length));
        
        byte[] response = HTTPMessageBuilder.buildResponse("HTTP/1.1", 200, "OK", headers, bodyBytes);
        String responseStr = new String(response);
        
        assertTrue("Should preserve Unicode in body", responseStr.endsWith(unicodeBody));
    }
}
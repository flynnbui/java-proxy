package proxy.http;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Unit tests for HTTPParser class.
 * Tests HTTP message parsing functionality.
 */
public class HTTPParserTest {
    
    @Test
    public void testParseSimpleGetRequest() throws HTTPParseException, IOException {
        String rawRequest = "GET http://example.com/path HTTP/1.1\r\n" +
                           "Host: example.com\r\n" +
                           "User-Agent: TestAgent/1.0\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("GET", request.getMethod());
        assertEquals("http://example.com/path", request.getTarget());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("example.com", request.getHeader("Host"));
        assertEquals("TestAgent/1.0", request.getHeader("User-Agent"));
        assertFalse(request.hasBody());
    }
    
    @Test
    public void testParsePostRequestWithBody() throws HTTPParseException, IOException {
        String requestBody = "{\"test\": \"data\"}";
        String rawRequest = "POST http://api.example.com/data HTTP/1.1\r\n" +
                           "Host: api.example.com\r\n" +
                           "Content-Type: application/json\r\n" +
                           "Content-Length: " + requestBody.length() + "\r\n" +
                           "\r\n" +
                           requestBody;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("POST", request.getMethod());
        assertEquals("http://api.example.com/data", request.getTarget());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals(String.valueOf(requestBody.length()), request.getHeader("Content-Length"));
        assertTrue(request.hasBody());
        assertEquals(requestBody, new String(request.getBody()));
    }
    
    @Test
    public void testParseConnectRequest() throws HTTPParseException, IOException {
        String rawRequest = "CONNECT example.com:443 HTTP/1.1\r\n" +
                           "Host: example.com:443\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("CONNECT", request.getMethod());
        assertEquals("example.com:443", request.getTarget());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("example.com:443", request.getHeader("Host"));
        assertFalse(request.hasBody());
    }
    
    @Test
    public void testParseResponse200() throws HTTPParseException, IOException {
        String responseBody = "<html><body>Hello World</body></html>";
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: " + responseBody.length() + "\r\n" +
                            "Server: TestServer/1.0\r\n" +
                            "\r\n" +
                            responseBody;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "GET");
        
        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());
        assertEquals("text/html", response.getHeader("Content-Type"));
        assertEquals(String.valueOf(responseBody.length()), response.getHeader("Content-Length"));
        assertEquals("TestServer/1.0", response.getHeader("Server"));
        assertTrue(response.hasBody("GET"));
        assertEquals(responseBody, new String(response.getBody()));
    }
    
    @Test
    public void testParseResponse404() throws HTTPParseException, IOException {
        String errorBody = "Not Found";
        String rawResponse = "HTTP/1.1 404 Not Found\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: " + errorBody.length() + "\r\n" +
                            "\r\n" +
                            errorBody;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "GET");
        
        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(404, response.getStatusCode());
        assertEquals("Not Found", response.getReasonPhrase());
        assertEquals(errorBody, new String(response.getBody()));
    }
    
    @Test
    public void testParseHeadResponse() throws HTTPParseException, IOException {
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: 1024\r\n" +
                            "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "HEAD");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("1024", response.getHeader("Content-Length"));
        assertFalse(response.hasBody("HEAD")); // HEAD responses don't have body
        assertEquals(0, response.getBody().length);
    }
    
    @Test
    public void testParseResponseWithTransferEncoding() throws HTTPParseException, IOException {
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Transfer-Encoding: chunked\r\n" +
                            "\r\n" +
                            "5\r\nHello\r\n" +
                            "6\r\n World\r\n" +
                            "0\r\n\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "GET");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("chunked", response.getHeader("Transfer-Encoding"));
        assertTrue(response.hasTransferEncoding());
        assertTrue(response.hasBody("GET"));
        assertEquals("Hello World", new String(response.getBody()));
    }
    
    @Test
    public void testParseCaseInsensitiveHeaders() throws HTTPParseException, IOException {
        String rawRequest = "GET http://example.com/ HTTP/1.1\r\n" +
                           "HOST: example.com\r\n" +
                           "content-type: text/plain\r\n" +
                           "Content-Length: 0\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("example.com", request.getHeader("host"));
        assertEquals("example.com", request.getHeader("HOST"));
        assertEquals("example.com", request.getHeader("Host"));
        assertEquals("text/plain", request.getHeader("Content-Type"));
        assertEquals("text/plain", request.getHeader("content-type"));
    }
    
    @Test
    public void testParseHeadersWithWhitespace() throws HTTPParseException, IOException {
        String rawRequest = "GET http://example.com/ HTTP/1.1\r\n" +
                           "Host:   example.com  \r\n" +
                           "User-Agent:\tTestAgent/1.0\t\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("example.com", request.getHeader("Host"));
        assertEquals("TestAgent/1.0", request.getHeader("User-Agent"));
    }
    
    @Test(expected = HTTPParseException.class)
    public void testParseInvalidRequestLine() throws HTTPParseException, IOException {
        String rawRequest = "INVALID REQUEST LINE\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPParser.parseRequest(inputStream);
    }
    
    @Test(expected = HTTPParseException.class)
    public void testParseInvalidStatusLine() throws HTTPParseException, IOException {
        String rawResponse = "INVALID STATUS LINE\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPParser.parseResponse(inputStream, "GET");
    }
    
    @Test(expected = HTTPParseException.class)
    public void testParseEmptyRequest() throws HTTPParseException, IOException {
        String rawRequest = "";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPParser.parseRequest(inputStream);
    }
    
    @Test(expected = HTTPParseException.class)
    public void testParseRequestMissingVersion() throws HTTPParseException, IOException {
        String rawRequest = "GET http://example.com/\r\n\r\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPParser.parseRequest(inputStream);
    }
    
    @Test(expected = HTTPParseException.class)
    public void testParseInvalidContentLength() throws HTTPParseException, IOException {
        String rawRequest = "POST http://example.com/ HTTP/1.1\r\n" +
                           "Content-Length: not-a-number\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPParser.parseRequest(inputStream);
    }
    
    @Test
    public void testParseMultilineHeaders() throws HTTPParseException, IOException {
        // HTTP allows header continuation with leading whitespace
        String rawRequest = "GET http://example.com/ HTTP/1.1\r\n" +
                           "Host: example.com\r\n" +
                           "User-Agent: TestAgent/1.0\r\n" +
                           " Extended-Info\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        // Should handle header continuation
        assertEquals("example.com", request.getHeader("Host"));
        assertTrue("Should have parsed the request", request.getMethod().equals("GET"));
    }
    
    @Test
    public void testParseRequestWithQueryParameters() throws HTTPParseException, IOException {
        String rawRequest = "GET http://example.com/search?q=test&limit=10&sort=date HTTP/1.1\r\n" +
                           "Host: example.com\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("GET", request.getMethod());
        assertEquals("http://example.com/search?q=test&limit=10&sort=date", request.getTarget());
    }
    
    @Test
    public void testParseResponseWithCustomHeaders() throws HTTPParseException, IOException {
        String rawResponse = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "X-Custom-Header: custom-value\r\n" +
                            "X-Rate-Limit: 1000\r\n" +
                            "Content-Length: 2\r\n" +
                            "\r\n" +
                            "{}";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "GET");
        
        assertEquals("application/json", response.getHeader("Content-Type"));
        assertEquals("custom-value", response.getHeader("X-Custom-Header"));
        assertEquals("1000", response.getHeader("X-Rate-Limit"));
        assertEquals("{}", new String(response.getBody()));
    }
    
    @Test
    public void testParseResponseWithoutReasonPhrase() throws HTTPParseException, IOException {
        String rawResponse = "HTTP/1.1 200\r\n" +
                            "Content-Length: 0\r\n" +
                            "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawResponse.getBytes());
        HTTPResponse response = HTTPParser.parseResponse(inputStream, "GET");
        
        assertEquals(200, response.getStatusCode());
        assertEquals("", response.getReasonPhrase()); // Empty reason phrase
    }
    
    @Test
    public void testParseRequestWithOriginForm() throws HTTPParseException, IOException {
        String rawRequest = "GET /path/to/resource HTTP/1.1\r\n" +
                           "Host: example.com\r\n" +
                           "\r\n";
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("GET", request.getMethod());
        assertEquals("/path/to/resource", request.getTarget());
        assertEquals("example.com", request.getHeader("Host"));
    }
    
    @Test
    public void testParseLargeBody() throws HTTPParseException, IOException {
        String largeBody = "x".repeat(10000); // 10KB body
        String rawRequest = "POST http://example.com/upload HTTP/1.1\r\n" +
                           "Content-Type: text/plain\r\n" +
                           "Content-Length: " + largeBody.length() + "\r\n" +
                           "\r\n" +
                           largeBody;
        
        ByteArrayInputStream inputStream = new ByteArrayInputStream(rawRequest.getBytes());
        HTTPRequest request = HTTPParser.parseRequest(inputStream);
        
        assertEquals("POST", request.getMethod());
        assertTrue(request.hasBody());
        assertEquals(largeBody.length(), request.getBody().length);
        assertEquals(largeBody, new String(request.getBody()));
    }
}
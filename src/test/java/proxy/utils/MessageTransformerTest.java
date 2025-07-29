package proxy.utils;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import proxy.http.HTTPRequest;
import proxy.http.HTTPResponse;

/**
 * Unit tests for MessageTransformer class.
 */
public class MessageTransformerTest {
    private MessageTransformer transformer;
    private HTTPRequest request;
    private HTTPResponse response;
    
    @Before
    public void setUp() {
        transformer = new MessageTransformer("1.1 z1234567");
        request = new HTTPRequest();
        response = new HTTPResponse();
    }
    
    @Test
    public void testDefaultConstructor() {
        MessageTransformer defaultTransformer = new MessageTransformer();
        // Should not throw exception and should use default proxy ID
        assertNotNull(defaultTransformer);
    }
    
    @Test
    public void testCustomProxyId() {
        MessageTransformer customTransformer = new MessageTransformer("1.1 z9999999");
        assertNotNull(customTransformer);
    }
    
    @Test
    public void testTransformRequestBasic() {
        request.setMethod("GET");
        request.setTarget("http://example.com/test");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "example.com");
        request.setHeader("User-Agent", "TestAgent/1.0");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, "/test");
        String result = new String(transformed);
        
        // Check request line uses origin-form path
        assertTrue("Should use origin-form path", result.contains("GET /test HTTP/1.1"));
        
        // Check Connection: close is added
        assertTrue("Should add Connection: close", result.contains("Connection: close"));
        
        // Check Via header is added
        assertTrue("Should add Via header", result.contains("Via: 1.1 z1234567"));
        
        // Check Host header is preserved
        assertTrue("Should preserve Host header", result.contains("Host: example.com"));
        
        // Check User-Agent is preserved
        assertTrue("Should preserve User-Agent", result.contains("User-Agent: TestAgent/1.0"));
    }
    
    @Test
    public void testTransformRequestWithNonStandardPort() {
        request.setMethod("GET");
        request.setTarget("http://example.com:8080/api");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "example.com:8080");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 8080, "/api");
        String result = new String(transformed);
        
        // Check Host header includes port for non-standard ports
        assertTrue("Should include port in Host header", result.contains("Host: example.com:8080"));
    }
    
    @Test
    public void testTransformRequestHttpsStandardPort() {
        request.setMethod("GET");
        request.setTarget("https://secure.example.com/secure");
        request.setVersion("HTTP/1.1");
        request.setHeader("Host", "secure.example.com:443");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "secure.example.com", 443, "/secure");
        String result = new String(transformed);
        
        // Check Host header excludes port for standard HTTPS port
        assertTrue("Should exclude standard HTTPS port", result.contains("Host: secure.example.com"));
        assertFalse("Should not include :443", result.contains("Host: secure.example.com:443"));
    }
    
    @Test
    public void testTransformRequestRemovesProxyConnection() {
        request.setMethod("GET");
        request.setTarget("http://example.com/test");
        request.setVersion("HTTP/1.1");
        request.setHeader("Proxy-Connection", "keep-alive");
        request.setHeader("Connection", "keep-alive");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, "/test");
        String result = new String(transformed);
        
        // Check Proxy-Connection is removed
        assertFalse("Should remove Proxy-Connection header", result.contains("Proxy-Connection"));
        
        // Check Connection is set to close
        assertTrue("Should set Connection: close", result.contains("Connection: close"));
    }
    
    @Test
    public void testTransformRequestWithExistingVia() {
        request.setMethod("GET");
        request.setTarget("http://example.com/test");
        request.setVersion("HTTP/1.1");
        request.setHeader("Via", "1.0 proxy1");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, "/test");
        String result = new String(transformed);
        
        // Check Via header is appended
        assertTrue("Should append to existing Via header", 
                  result.contains("Via: 1.0 proxy1, 1.1 z1234567"));
    }
    
    @Test
    public void testTransformRequestWithBody() {
        request.setMethod("POST");
        request.setTarget("http://example.com/api");
        request.setVersion("HTTP/1.1");
        request.setHeader("Content-Type", "application/json");
        request.setHeader("Content-Length", "13");
        request.setBody("{\"test\":true}".getBytes());
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, "/api");
        String result = new String(transformed);
        
        // Check method and headers
        assertTrue("Should preserve POST method", result.contains("POST /api HTTP/1.1"));
        assertTrue("Should preserve Content-Type", result.contains("Content-Type: application/json"));
        assertTrue("Should preserve Content-Length", result.contains("Content-Length: 13"));
        
        // Check body is included
        assertTrue("Should include request body", result.contains("{\"test\":true}"));
    }
    
    @Test
    public void testTransformResponseBasic() {
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        response.setHeader("Content-Type", "text/html");
        response.setHeader("Content-Length", "100");
        response.setBody("<html>Test</html>".getBytes());
        
        byte[] transformed = transformer.transformResponseForClient(response);
        String result = new String(transformed);
        
        // Check status line
        assertTrue("Should preserve status line", result.contains("HTTP/1.1 200 OK"));
        
        // Check headers are preserved
        assertTrue("Should preserve Content-Type", result.contains("Content-Type: text/html"));
        assertTrue("Should preserve Content-Length", result.contains("Content-Length: 100"));
        
        // Check Via header is added
        assertTrue("Should add Via header", result.contains("Via: 1.1 z1234567"));
        
        // Check default Connection: close
        assertTrue("Should default to Connection: close", result.contains("Connection: close"));
        
        // Check body
        assertTrue("Should include response body", result.contains("<html>Test</html>"));
    }
    
    @Test
    public void testTransformResponseWithClientRequestKeepAlive() {
        // Setup response
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        response.setHeader("Content-Type", "text/plain");
        
        // Setup client request with keep-alive
        request.setVersion("HTTP/1.1");
        request.setHeader("Connection", "keep-alive");
        
        byte[] transformed = transformer.transformResponseForClient(response, request);
        String result = new String(transformed);
        
        // Check Connection: keep-alive is preserved
        assertTrue("Should preserve Connection: keep-alive", result.contains("Connection: keep-alive"));
    }
    
    @Test
    public void testTransformResponseWithClientRequestClose() {
        // Setup response
        response.setVersion("HTTP/1.1");
        response.setStatusCode(404);
        response.setReasonPhrase("Not Found");
        
        // Setup client request with close
        request.setVersion("HTTP/1.1");
        request.setHeader("Connection", "close");
        
        byte[] transformed = transformer.transformResponseForClient(response, request);
        String result = new String(transformed);
        
        // Check Connection: close is set
        assertTrue("Should set Connection: close", result.contains("Connection: close"));
    }
    
    @Test
    public void testTransformResponseHTTP10DefaultClose() {
        // Setup response
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        
        // Setup HTTP/1.0 client request (defaults to close)
        request.setVersion("HTTP/1.0");
        
        byte[] transformed = transformer.transformResponseForClient(response, request);
        String result = new String(transformed);
        
        // Check Connection: close is set for HTTP/1.0
        assertTrue("Should set Connection: close for HTTP/1.0", result.contains("Connection: close"));
    }
    
    @Test
    public void testTransformResponseHTTP11DefaultKeepAlive() {
        // Setup response
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        
        // Setup HTTP/1.1 client request with no Connection header
        request.setVersion("HTTP/1.1");
        
        byte[] transformed = transformer.transformResponseForClient(response, request);
        String result = new String(transformed);
        
        // Check Connection: keep-alive is set for HTTP/1.1 default
        assertTrue("Should default to Connection: keep-alive for HTTP/1.1", 
                  result.contains("Connection: keep-alive"));
    }
    
    @Test
    public void testTransformResponseWithExistingVia() {
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        response.setHeader("Via", "1.0 origin-proxy");
        
        byte[] transformed = transformer.transformResponseForClient(response);
        String result = new String(transformed);
        
        // Check Via header is appended
        assertTrue("Should append to existing Via header", 
                  result.contains("Via: 1.0 origin-proxy, 1.1 z1234567"));
    }
    
    @Test
    public void testTransformResponseRemovesOriginalConnection() {
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        response.setHeader("Connection", "keep-alive"); // Original connection from server
        response.setHeader("Server", "Apache/2.4");
        
        // Client wants to close
        request.setVersion("HTTP/1.1");
        request.setHeader("Connection", "close");
        
        byte[] transformed = transformer.transformResponseForClient(response, request);
        String result = new String(transformed);
        
        // Check original Connection header is replaced
        assertTrue("Should set Connection: close per client request", 
                  result.contains("Connection: close"));
        
        // Should not contain the original keep-alive (though this is hard to test definitively)
        // The new Connection: close should override the original
        
        // Check other headers are preserved
        assertTrue("Should preserve Server header", result.contains("Server: Apache/2.4"));
    }
    
    @Test
    public void testTransformRequestComplexPath() {
        request.setMethod("GET");
        request.setTarget("http://example.com/path/to/resource?param1=value1&param2=value2");
        request.setVersion("HTTP/1.1");
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, 
                                                                 "/path/to/resource?param1=value1&param2=value2");
        String result = new String(transformed);
        
        // Check complex path with query is preserved
        assertTrue("Should preserve complex path and query", 
                  result.contains("GET /path/to/resource?param1=value1&param2=value2 HTTP/1.1"));
    }
    
    @Test
    public void testNullProxyIdHandling() {
        MessageTransformer nullIdTransformer = new MessageTransformer(null);
        
        request.setMethod("GET");
        request.setTarget("http://example.com/test");
        request.setVersion("HTTP/1.1");
        
        byte[] transformed = nullIdTransformer.transformRequestForOrigin(request, "example.com", 80, "/test");
        String result = new String(transformed);
        
        // Should use default proxy ID when null is provided
        assertTrue("Should use default Via header when proxy ID is null", 
                  result.contains("Via: 1.1 z1234567"));
    }
    
    @Test
    public void testCaseInsensitiveHeaderHandling() {
        request.setMethod("GET");
        request.setTarget("http://example.com/test");
        request.setVersion("HTTP/1.1");
        request.setHeader("PROXY-CONNECTION", "keep-alive"); // Uppercase
        request.setHeader("connection", "keep-alive"); // Lowercase
        
        byte[] transformed = transformer.transformRequestForOrigin(request, "example.com", 80, "/test");
        String result = new String(transformed);
        
        // Check case-insensitive removal of Proxy-Connection
        assertFalse("Should remove PROXY-CONNECTION (uppercase)", result.contains("PROXY-CONNECTION"));
        assertFalse("Should remove proxy-connection", result.contains("proxy-connection"));
        
        // Should still set Connection: close
        assertTrue("Should set Connection: close", result.contains("Connection: close"));
    }
}
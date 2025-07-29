package proxy.http;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for HTTPResponse class.
 */
public class HTTPResponseTest {
    private HTTPResponse response;
    
    @Before
    public void setUp() {
        response = new HTTPResponse();
    }
    
    @Test
    public void testBasicGettersSetters() {
        response.setVersion("HTTP/1.1");
        response.setStatusCode(200);
        response.setReasonPhrase("OK");
        
        assertEquals("HTTP/1.1", response.getVersion());
        assertEquals(200, response.getStatusCode());
        assertEquals("OK", response.getReasonPhrase());
    }
    
    @Test
    public void testHeadersCaseInsensitive() {
        response.setHeader("Content-Type", "text/html");
        response.setHeader("SERVER", "Apache/2.4");
        
        assertEquals("text/html", response.getHeader("content-type"));
        assertEquals("text/html", response.getHeader("Content-Type"));
        assertEquals("Apache/2.4", response.getHeader("server"));
        assertEquals("Apache/2.4", response.getHeader("SERVER"));
    }
    
    @Test
    public void testRawHeadersPreserveCase() {
        response.setHeader("Content-Type", "text/html");
        response.setHeader("SERVER", "Apache/2.4");
        
        assertTrue(response.getRawHeaders().containsKey("Content-Type"));
        assertTrue(response.getRawHeaders().containsKey("SERVER"));
        assertEquals("text/html", response.getRawHeaders().get("Content-Type"));
        assertEquals("Apache/2.4", response.getRawHeaders().get("SERVER"));
    }
    
    @Test
    public void testHasBodyDifferentMethods() {
        // Default should not have body (no Content-Length or Transfer-Encoding)
        assertFalse(response.hasBody("HEAD"));
        assertFalse(response.hasBody("GET"));
        
        // 204 No Content should not have body
        response.setStatusCode(204);
        response.setHeader("Content-Length", "0");
        assertFalse(response.hasBody("GET"));
        
        // 304 Not Modified should not have body
        response.setStatusCode(304);
        response.setHeader("Content-Length", "0");
        assertFalse(response.hasBody("GET"));
        
        // 200 OK with Content-Length should have body for GET
        response.setStatusCode(200);
        response.setHeader("Content-Length", "100");
        assertTrue(response.hasBody("GET"));
        assertFalse(response.hasBody("HEAD")); // HEAD never has body
    }
    
    @Test
    public void testGetContentLength() throws HTTPParseException {
        assertNull(response.getContentLength());
        
        response.setHeader("Content-Length", "2048");
        assertEquals(Integer.valueOf(2048), response.getContentLength());
        
        response.setHeader("Content-Length", "0");
        assertEquals(Integer.valueOf(0), response.getContentLength());
    }
    
    @Test(expected = HTTPParseException.class)
    public void testGetContentLengthInvalid() throws HTTPParseException {
        response.setHeader("Content-Length", "not-a-number");
        response.getContentLength();
    }
    
    @Test
    public void testHasTransferEncoding() {
        assertFalse(response.hasTransferEncoding());
        
        response.setHeader("Transfer-Encoding", "chunked");
        assertTrue(response.hasTransferEncoding());
        
        response.setHeader("transfer-encoding", "gzip, chunked");
        assertTrue(response.hasTransferEncoding());
    }
    
    @Test
    public void testBodyHandling() {
        assertEquals(0, response.getBody().length);
        
        byte[] body = "<html><body>Hello</body></html>".getBytes();
        response.setBody(body);
        assertArrayEquals(body, response.getBody());
        
        response.setBody(null);
        assertEquals(0, response.getBody().length);
    }
    
    @Test
    public void testToString() {
        response.setVersion("HTTP/1.1");
        response.setStatusCode(404);
        response.setReasonPhrase("Not Found");
        
        String str = response.toString();
        assertTrue(str.contains("HTTP/1.1"));
        assertTrue(str.contains("404"));
        assertTrue(str.contains("Not Found"));
    }
    
    @Test
    public void testSuccessStatusCodes() {
        // Test various success status codes
        int[] successCodes = {200, 201, 202, 204, 206};
        for (int code : successCodes) {
            response.setStatusCode(code);
            response.setHeader("Content-Length", "100"); // Add Content-Length
            // All should have body except 204
            if (code == 204) {
                assertFalse("Status " + code + " should not have body", response.hasBody("GET"));
            } else {
                assertTrue("Status " + code + " should have body", response.hasBody("GET"));
            }
        }
    }
    
    @Test
    public void testErrorStatusCodes() {
        // Test various error status codes - they should have body
        int[] errorCodes = {400, 404, 500, 502, 504};
        for (int code : errorCodes) {
            response.setStatusCode(code);
            response.setHeader("Content-Length", "100"); // Add Content-Length
            assertTrue("Status " + code + " should have body", response.hasBody("GET"));
            assertFalse("Status " + code + " should not have body for HEAD", response.hasBody("HEAD"));
        }
    }
    
    @Test
    public void testRedirectStatusCodes() {
        // Test redirect status codes - they should have body
        int[] redirectCodes = {301, 302, 303, 307, 308};
        for (int code : redirectCodes) {
            response.setStatusCode(code);
            response.setHeader("Content-Length", "100"); // Add Content-Length
            assertTrue("Status " + code + " should have body", response.hasBody("GET"));
        }
    }
}
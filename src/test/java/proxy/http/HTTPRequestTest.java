package proxy.http;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for HTTPRequest class.
 */
public class HTTPRequestTest {
    private HTTPRequest request;
    
    @Before
    public void setUp() {
        request = new HTTPRequest();
    }
    
    @Test
    public void testBasicGettersSetters() {
        request.setMethod("GET");
        request.setTarget("http://example.com/");
        request.setVersion("HTTP/1.1");
        
        assertEquals("GET", request.getMethod());
        assertEquals("http://example.com/", request.getTarget());
        assertEquals("HTTP/1.1", request.getVersion());
    }
    
    @Test
    public void testHeadersCaseInsensitive() {
        request.setHeader("Content-Type", "application/json");
        request.setHeader("HOST", "example.com");
        
        assertEquals("application/json", request.getHeader("content-type"));
        assertEquals("application/json", request.getHeader("Content-Type"));
        assertEquals("example.com", request.getHeader("host"));
        assertEquals("example.com", request.getHeader("HOST"));
    }
    
    @Test
    public void testRawHeadersPreserveCase() {
        request.setHeader("Content-Type", "application/json");
        request.setHeader("HOST", "example.com");
        
        assertTrue(request.getRawHeaders().containsKey("Content-Type"));
        assertTrue(request.getRawHeaders().containsKey("HOST"));
        assertEquals("application/json", request.getRawHeaders().get("Content-Type"));
        assertEquals("example.com", request.getRawHeaders().get("HOST"));
    }
    
    @Test
    public void testHasBodyWithContentLength() {
        assertFalse(request.hasBody());
        
        request.setHeader("Content-Length", "100");
        assertTrue(request.hasBody());
        
        request.setHeader("Content-Length", "0");
        assertTrue(request.hasBody()); // Still has body header even if length is 0
    }
    
    @Test
    public void testGetContentLength() throws HTTPParseException {
        assertNull(request.getContentLength());
        
        request.setHeader("Content-Length", "1024");
        assertEquals(Integer.valueOf(1024), request.getContentLength());
        
        request.setHeader("Content-Length", "0");
        assertEquals(Integer.valueOf(0), request.getContentLength());
    }
    
    @Test(expected = HTTPParseException.class)
    public void testGetContentLengthInvalid() throws HTTPParseException {
        request.setHeader("Content-Length", "invalid");
        request.getContentLength();
    }
    
    @Test
    public void testHasTransferEncoding() {
        assertFalse(request.hasTransferEncoding());
        
        request.setHeader("Transfer-Encoding", "chunked");
        assertTrue(request.hasTransferEncoding());
    }
    
    @Test
    public void testBodyHandling() {
        assertEquals(0, request.getBody().length);
        
        byte[] body = "Hello World".getBytes();
        request.setBody(body);
        assertArrayEquals(body, request.getBody());
        
        request.setBody(null);
        assertEquals(0, request.getBody().length);
    }
    
    @Test
    public void testToString() {
        request.setMethod("POST");
        request.setTarget("http://example.com/api");
        request.setVersion("HTTP/1.1");
        
        String str = request.toString();
        assertTrue(str.contains("POST"));
        assertTrue(str.contains("http://example.com/api"));
        assertTrue(str.contains("HTTP/1.1"));
    }
    
    @Test
    public void testGetHeaderNonExistent() {
        assertNull(request.getHeader("Non-Existent"));
        assertNull(request.getHeader("content-type"));
    }
    
    @Test
    public void testHeaderOverwrite() {
        request.setHeader("Content-Type", "text/plain");
        assertEquals("text/plain", request.getHeader("content-type"));
        
        request.setHeader("content-type", "application/json");
        assertEquals("application/json", request.getHeader("content-type"));
        
        // Raw headers should contain the latest case
        assertTrue(request.getRawHeaders().containsKey("content-type"));
        assertEquals("application/json", request.getRawHeaders().get("content-type"));
    }
}
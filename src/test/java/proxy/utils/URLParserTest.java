package proxy.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for URLParser utility class.
 */
public class URLParserTest {
    
    @Test
    public void testParseAbsoluteUrlBasic() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("http://example.com/path");
        
        assertEquals("http", result[0]); // scheme
        assertEquals("example.com", result[1]); // hostname
        assertEquals("80", result[2]); // port (default)
        assertEquals("/path", result[3]); // path
    }
    
    @Test
    public void testParseAbsoluteUrlHttps() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("https://secure.example.com/api");
        
        assertEquals("https", result[0]);
        assertEquals("secure.example.com", result[1]);
        assertEquals("443", result[2]); // default HTTPS port
        assertEquals("/api", result[3]);
    }
    
    @Test
    public void testParseAbsoluteUrlWithExplicitPort() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("http://example.com:8080/test");
        
        assertEquals("http", result[0]);
        assertEquals("example.com", result[1]);
        assertEquals("8080", result[2]); // explicit port
        assertEquals("/test", result[3]);
    }
    
    @Test
    public void testParseAbsoluteUrlWithQuery() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("http://example.com/search?q=test&limit=10");
        
        assertEquals("http", result[0]);
        assertEquals("example.com", result[1]);
        assertEquals("80", result[2]);
        assertEquals("/search?q=test&limit=10", result[3]); // path with query
    }
    
    @Test
    public void testParseAbsoluteUrlEmptyPath() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("http://example.com");
        
        assertEquals("http", result[0]);
        assertEquals("example.com", result[1]);
        assertEquals("80", result[2]);
        assertEquals("/", result[3]); // empty path becomes "/"
    }
    
    @Test
    public void testParseAbsoluteUrlRootPath() throws ProxyException {
        String[] result = URLParser.parseAbsoluteUrl("http://example.com/");
        
        assertEquals("http", result[0]);
        assertEquals("example.com", result[1]);
        assertEquals("80", result[2]);
        assertEquals("/", result[3]);
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAbsoluteUrlInvalidScheme() throws ProxyException {
        URLParser.parseAbsoluteUrl("ftp://example.com/file");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAbsoluteUrlRelative() throws ProxyException {
        URLParser.parseAbsoluteUrl("/relative/path");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAbsoluteUrlNoHost() throws ProxyException {
        URLParser.parseAbsoluteUrl("http:///no-host");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAbsoluteUrlMalformed() throws ProxyException {
        URLParser.parseAbsoluteUrl("http://malformed url with spaces");
    }
    
    @Test
    public void testParseAuthorityFormBasic() throws ProxyException {
        String[] result = URLParser.parseAuthorityForm("example.com:443");
        
        assertEquals("example.com", result[0]); // hostname
        assertEquals("443", result[1]); // port
    }
    
    @Test
    public void testParseAuthorityFormWithSubdomain() throws ProxyException {
        String[] result = URLParser.parseAuthorityForm("api.example.com:8080");
        
        assertEquals("api.example.com", result[0]);
        assertEquals("8080", result[1]);
    }
    
    @Test
    public void testParseAuthorityFormIPv4() throws ProxyException {
        String[] result = URLParser.parseAuthorityForm("192.168.1.1:9000");
        
        assertEquals("192.168.1.1", result[0]);
        assertEquals("9000", result[1]);
    }
    
    @Test
    public void testParseAuthorityFormIPv6() throws ProxyException {
        // IPv6 addresses should be in brackets for authority form
        String[] result = URLParser.parseAuthorityForm("[::1]:443");
        
        assertEquals("[::1]", result[0]);
        assertEquals("443", result[1]);
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormWithScheme() throws ProxyException {
        URLParser.parseAuthorityForm("http://example.com:443");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormWithHttpsScheme() throws ProxyException {
        URLParser.parseAuthorityForm("https://example.com:443");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormNoPort() throws ProxyException {
        URLParser.parseAuthorityForm("example.com");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormEmptyHost() throws ProxyException {
        URLParser.parseAuthorityForm(":443");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormInvalidPort() throws ProxyException {
        URLParser.parseAuthorityForm("example.com:not-a-port");
    }
    
    @Test(expected = ProxyException.class)
    public void testParseAuthorityFormNegativePort() throws ProxyException {
        URLParser.parseAuthorityForm("example.com:-443");
    }
    
    @Test
    public void testIsSelfLoopLocalhost() {
        assertTrue(URLParser.isSelfLoop("localhost", 8080, 8080));
        assertTrue(URLParser.isSelfLoop("LOCALHOST", 8080, 8080)); // case insensitive
        assertFalse(URLParser.isSelfLoop("localhost", 8080, 8081)); // different port
    }
    
    @Test
    public void testIsSelfLoop127() {
        assertTrue(URLParser.isSelfLoop("127.0.0.1", 3128, 3128));
        assertFalse(URLParser.isSelfLoop("127.0.0.1", 3128, 3129)); // different port
    }
    
    @Test
    public void testIsSelfLoopDifferentHost() {
        assertFalse(URLParser.isSelfLoop("example.com", 8080, 8080));
        assertFalse(URLParser.isSelfLoop("192.168.1.1", 8080, 8080));
        assertFalse(URLParser.isSelfLoop("google.com", 443, 443));
    }
    
    @Test
    public void testComplexUrls() throws ProxyException {
        // Test URL with fragment (should be ignored)
        String[] result = URLParser.parseAbsoluteUrl("http://example.com/page#fragment");
        assertEquals("/page", result[3]); // fragment should be stripped
        
        // Test URL with encoded characters
        result = URLParser.parseAbsoluteUrl("http://example.com/search?q=hello%20world");
        assertEquals("/search?q=hello%20world", result[3]);
        
        // Test URL with multiple query parameters
        result = URLParser.parseAbsoluteUrl("http://example.com/api?param1=value1&param2=value2&param3=value3");
        assertEquals("/api?param1=value1&param2=value2&param3=value3", result[3]);
    }
    
    @Test
    public void testEdgeCasesPorts() throws ProxyException {
        // Test port 0 (should be kept as explicit)
        String[] result = URLParser.parseAbsoluteUrl("http://example.com:0/");
        assertEquals("0", result[2]);
        
        // Test high port number
        result = URLParser.parseAbsoluteUrl("http://example.com:65535/");
        assertEquals("65535", result[2]);
    }
    
    @Test
    public void testAuthorityFormColonsInIPv6() throws ProxyException {
        // Test IPv6 with multiple colons - should use last colon for port
        String[] result = URLParser.parseAuthorityForm("[2001:db8::1]:443");
        assertEquals("[2001:db8::1]", result[0]);
        assertEquals("443", result[1]);
    }
    
    @Test
    public void testCaseSensitivity() throws ProxyException {
        // Scheme should be case insensitive in isSelfLoop context
        String[] result = URLParser.parseAbsoluteUrl("HTTP://EXAMPLE.COM/PATH");
        assertEquals("HTTP", result[0]); // Scheme case preserved in parsing
        assertEquals("EXAMPLE.COM", result[1]); // Host case preserved
        
        // Self-loop detection should be case insensitive for localhost
        assertTrue(URLParser.isSelfLoop("LocalHost", 8080, 8080));
        assertTrue(URLParser.isSelfLoop("LOCALHOST", 8080, 8080));
    }
}
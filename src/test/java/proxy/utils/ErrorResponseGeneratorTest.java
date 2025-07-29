package proxy.utils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ErrorResponseGenerator utility class.
 */
public class ErrorResponseGeneratorTest {
    
    @Test
    public void testBadRequest() {
        byte[] response = ErrorResponseGenerator.badRequest("Invalid request format");
        String responseStr = new String(response);
        
        assertTrue("Should contain 400 status", responseStr.contains("400"));
        assertTrue("Should contain Bad Request", responseStr.contains("Bad Request"));
        assertTrue("Should contain error message", responseStr.contains("Invalid request format"));
        assertTrue("Should have proper HTTP structure", responseStr.contains("HTTP/1.1"));
        assertTrue("Should have Content-Type header", responseStr.contains("Content-Type:"));
        assertTrue("Should have Content-Length header", responseStr.contains("Content-Length:"));
        assertTrue("Should end headers with empty line", responseStr.contains("\r\n\r\n"));
    }
    
    @Test
    public void testBadGateway() {
        byte[] response = ErrorResponseGenerator.badGateway("Connection failed");
        String responseStr = new String(response);
        
        assertTrue("Should contain 502 status", responseStr.contains("502"));
        assertTrue("Should contain Bad Gateway", responseStr.contains("Bad Gateway"));
        assertTrue("Should contain error message", responseStr.contains("Connection failed"));
        assertTrue("Should have proper HTTP structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testGatewayTimeout() {
        byte[] response = ErrorResponseGenerator.gatewayTimeout("Request timeout");
        String responseStr = new String(response);
        
        assertTrue("Should contain 504 status", responseStr.contains("504"));
        assertTrue("Should contain Gateway Timeout", responseStr.contains("Gateway Timeout"));
        assertTrue("Should contain error message", responseStr.contains("Request timeout"));
        assertTrue("Should have proper HTTP structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testMisdirectedRequest() {
        byte[] response = ErrorResponseGenerator.misdirectedRequest("Self-loop detected");
        String responseStr = new String(response);
        
        assertTrue("Should contain 421 status", responseStr.contains("421"));
        assertTrue("Should contain Misdirected Request", responseStr.contains("Misdirected Request"));
        assertTrue("Should contain error message", responseStr.contains("Self-loop detected"));
        assertTrue("Should have proper HTTP structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testEmptyErrorMessage() {
        byte[] response = ErrorResponseGenerator.badRequest("");
        String responseStr = new String(response);
        
        assertTrue("Should still generate valid response with empty message", 
                  responseStr.contains("400"));
        assertTrue("Should have proper structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testNullErrorMessage() {
        byte[] response = ErrorResponseGenerator.badGateway(null);
        String responseStr = new String(response);
        
        assertTrue("Should handle null message gracefully", responseStr.contains("502"));
        assertTrue("Should have proper structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testLongErrorMessage() {
        String longMessage = "This is a very long error message that contains many details about what went wrong. ".repeat(10);
        byte[] response = ErrorResponseGenerator.badRequest(longMessage);
        String responseStr = new String(response);
        
        assertTrue("Should handle long messages", responseStr.contains("400"));
        assertTrue("Should contain the long message", responseStr.contains(longMessage));
        assertTrue("Should have proper structure", responseStr.contains("HTTP/1.1"));
    }
    
    @Test
    public void testSpecialCharactersInMessage() {
        String messageWithSpecialChars = "Error: <script>alert('test')</script> & \"quotes\" & 'apostrophes'";
        byte[] response = ErrorResponseGenerator.badRequest(messageWithSpecialChars);
        String responseStr = new String(response);
        
        assertTrue("Should handle special characters", responseStr.contains("400"));
        assertTrue("Should contain the message with special chars", 
                  responseStr.contains(messageWithSpecialChars));
    }
    
    @Test
    public void testUnicodeInMessage() {
        String unicodeMessage = "Unicode test: 你好 世界 🌍 café naïve résumé";
        byte[] response = ErrorResponseGenerator.badRequest(unicodeMessage);
        String responseStr = new String(response);
        
        assertTrue("Should handle Unicode characters", responseStr.contains("400"));
        assertTrue("Should contain Unicode message", responseStr.contains(unicodeMessage));
    }
    
    @Test
    public void testResponseFormat() {
        byte[] response = ErrorResponseGenerator.badRequest("Test error");
        String responseStr = new String(response);
        
        // Test specific format requirements
        assertTrue("Should start with HTTP/1.1", responseStr.startsWith("HTTP/1.1"));
        assertTrue("Should have CRLF line endings", responseStr.contains("\r\n"));
        assertTrue("Should have empty line separating headers from body", 
                  responseStr.contains("\r\n\r\n"));
        
        // Check headers
        assertTrue("Should have Content-Type header", 
                  responseStr.contains("Content-Type: text/html"));
        assertTrue("Should have Content-Length header", 
                  responseStr.contains("Content-Length:"));
        
        // Extract and verify Content-Length
        String[] lines = responseStr.split("\r\n");
        int contentLength = -1;
        int bodyStartIndex = -1;
        
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(lines[i].substring(15).trim());
            }
            if (lines[i].isEmpty()) {
                bodyStartIndex = i + 1;
                break;
            }
        }
        
        assertTrue("Should have valid Content-Length", contentLength > 0);
        assertTrue("Should have body after headers", bodyStartIndex > 0);
        
        // Verify body content matches Content-Length
        if (bodyStartIndex < lines.length) {
            StringBuilder body = new StringBuilder();
            for (int i = bodyStartIndex; i < lines.length; i++) {
                if (i > bodyStartIndex) body.append("\r\n");
                body.append(lines[i]);
            }
            assertEquals("Content-Length should match actual body length", 
                        contentLength, body.toString().getBytes().length);
        }
    }
    
    @Test
    public void testMultipleErrorResponses() {
        // Test that multiple calls generate consistent responses
        byte[] response1 = ErrorResponseGenerator.badRequest("Same message");
        byte[] response2 = ErrorResponseGenerator.badRequest("Same message");
        
        assertArrayEquals("Should generate identical responses for same input", 
                         response1, response2);
    }
    
    @Test
    public void testDifferentStatusCodes() {
        byte[] badRequest = ErrorResponseGenerator.badRequest("test");
        byte[] badGateway = ErrorResponseGenerator.badGateway("test");
        byte[] gatewayTimeout = ErrorResponseGenerator.gatewayTimeout("test");
        byte[] misdirected = ErrorResponseGenerator.misdirectedRequest("test");
        
        String badRequestStr = new String(badRequest);
        String badGatewayStr = new String(badGateway);
        String gatewayTimeoutStr = new String(gatewayTimeout);
        String misdirectedStr = new String(misdirected);
        
        assertTrue("400 response should contain 400", badRequestStr.contains("400"));
        assertTrue("502 response should contain 502", badGatewayStr.contains("502"));
        assertTrue("504 response should contain 504", gatewayTimeoutStr.contains("504"));
        assertTrue("421 response should contain 421", misdirectedStr.contains("421"));
        
        // Verify they're all different
        assertFalse("Responses should be different", badRequestStr.equals(badGatewayStr));
        assertFalse("Responses should be different", badGatewayStr.equals(gatewayTimeoutStr));
        assertFalse("Responses should be different", gatewayTimeoutStr.equals(misdirectedStr));
    }
    
    @Test
    public void testHtmlBodyFormat() {
        byte[] response = ErrorResponseGenerator.badRequest("Test error message");
        String responseStr = new String(response);
        
        // Check for basic HTML structure
        assertTrue("Should contain HTML DOCTYPE", responseStr.contains("<!DOCTYPE html>"));
        assertTrue("Should contain html tag", responseStr.contains("<html"));
        assertTrue("Should contain head section", responseStr.contains("<head>"));
        assertTrue("Should contain body section", responseStr.contains("<body>"));
        assertTrue("Should contain title", responseStr.contains("<title>"));
        assertTrue("Should contain h1 header", responseStr.contains("<h1>"));
        assertTrue("Should contain paragraph", responseStr.contains("<p>"));
        assertTrue("Should close html tag", responseStr.contains("</html>"));
        
        // Check that error message appears in body
        assertTrue("Error message should appear in HTML body", 
                  responseStr.contains("Test error message"));
    }
    
    @Test
    public void testNewlineInErrorMessage() {
        String messageWithNewlines = "Line 1\nLine 2\r\nLine 3";
        byte[] response = ErrorResponseGenerator.badRequest(messageWithNewlines);
        String responseStr = new String(response);
        
        assertTrue("Should handle newlines in message", responseStr.contains("400"));
        assertTrue("Should contain the message", responseStr.contains(messageWithNewlines));
    }
}
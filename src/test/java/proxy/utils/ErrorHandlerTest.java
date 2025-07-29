package proxy.utils;

import org.junit.Test;
import static org.junit.Assert.*;

import proxy.http.HTTPParseException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.io.IOException;

/**
 * Unit tests for ErrorHandler utility class.
 */
public class ErrorHandlerTest {
    
    @Test
    public void testMapHTTPParseException() {
        HTTPParseException e = new HTTPParseException("Invalid request line");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 400 Bad Request", responseStr.contains("400"));
        assertTrue("Should mention malformed HTTP request", responseStr.contains("Malformed HTTP request"));
        assertTrue("Should include original message", responseStr.contains("Invalid request line"));
    }
    
    @Test
    public void testMapUnknownHostException() {
        UnknownHostException e = new UnknownHostException("unknown-host.example.com");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention failed to resolve", responseStr.contains("Failed to resolve host"));
    }
    
    @Test
    public void testMapSocketTimeoutException() {
        SocketTimeoutException e = new SocketTimeoutException("Read timed out");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 504 Gateway Timeout", responseStr.contains("504"));
        assertTrue("Should mention timeout", responseStr.contains("timed out"));
    }
    
    @Test
    public void testMapConnectException() {
        ConnectException e = new ConnectException("Connection refused");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention connection refused", responseStr.contains("Connection refused"));
    }
    
    @Test
    public void testMapConnectExceptionGeneric() {
        ConnectException e = new ConnectException("Network unreachable");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention failed to connect", responseStr.contains("Failed to connect"));
    }
    
    @Test
    public void testMapIOExceptionNetworkUnreachable() {
        IOException e = new IOException("Network is unreachable");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention network unreachable", responseStr.contains("Network unreachable"));
    }
    
    @Test
    public void testMapIOExceptionConnectionReset() {
        IOException e = new IOException("Connection reset by peer");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention connection reset", responseStr.contains("Connection reset"));
    }
    
    @Test
    public void testMapIOExceptionBrokenPipe() {
        IOException e = new IOException("Broken pipe");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention closed unexpectedly", responseStr.contains("closed unexpectedly"));
    }
    
    @Test
    public void testMapIOExceptionGeneric() {
        IOException e = new IOException("Some other IO error");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention network error", responseStr.contains("Network error"));
    }
    
    @Test
    public void testMapProxyExceptionCouldNotResolve() {
        ProxyException e = new ProxyException("could not resolve host");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention failed to resolve", responseStr.contains("Failed to resolve"));
    }
    
    @Test
    public void testMapProxyExceptionCouldNotConnect() {
        ProxyException e = new ProxyException("could not connect to server");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention failed to resolve", responseStr.contains("Failed to resolve"));
    }
    
    @Test
    public void testMapProxyExceptionTimedOut() {
        ProxyException e = new ProxyException("request timed out");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 504 Gateway Timeout", responseStr.contains("504"));
        assertTrue("Should mention timeout", responseStr.contains("timeout"));
    }
    
    @Test
    public void testMapProxyExceptionConnectionRefused() {
        ProxyException e = new ProxyException("connection refused by server");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention connection refused", responseStr.contains("Connection refused"));
    }
    
    @Test
    public void testMapProxyExceptionNetworkUnreachable() {
        ProxyException e = new ProxyException("network unreachable");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention network unreachable", responseStr.contains("Network unreachable"));
    }
    
    @Test
    public void testMapProxyExceptionSelfLoop() {
        ProxyException e = new ProxyException("self-loop detected");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 421 Misdirected Request", responseStr.contains("421"));
        assertTrue("Should mention self-loop", responseStr.contains("Self-loop"));
    }
    
    @Test
    public void testMapProxyExceptionGeneric() {
        ProxyException e = new ProxyException("some other proxy error");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should include original message", responseStr.contains("some other proxy error"));
    }
    
    @Test
    public void testMapNullException() {
        byte[] response = ErrorHandler.mapExceptionToResponse(null);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention unknown error", responseStr.contains("Unknown error"));
    }
    
    @Test
    public void testMapGenericException() {
        RuntimeException e = new RuntimeException("Unexpected error");
        byte[] response = ErrorHandler.mapExceptionToResponse(e);
        
        String responseStr = new String(response);
        assertTrue("Should be 502 Bad Gateway", responseStr.contains("502"));
        assertTrue("Should mention internal proxy error", responseStr.contains("Internal proxy error"));
    }
    
    @Test
    public void testGetStatusCode() {
        assertEquals(400, ErrorHandler.getStatusCode(new HTTPParseException("test")));
        assertEquals(504, ErrorHandler.getStatusCode(new SocketTimeoutException("timeout")));
        assertEquals(421, ErrorHandler.getStatusCode(new ProxyException("self-loop detected")));
        assertEquals(504, ErrorHandler.getStatusCode(new ProxyException("timed out")));
        assertEquals(502, ErrorHandler.getStatusCode(new ProxyException("generic error")));
        assertEquals(502, ErrorHandler.getStatusCode(new RuntimeException("generic")));
    }
    
    @Test
    public void testIsRetriableError() {
        assertTrue("SocketTimeoutException should be retriable", 
                  ErrorHandler.isRetriableError(new SocketTimeoutException()));
        
        assertTrue("ConnectException with Connection refused should be retriable",
                  ErrorHandler.isRetriableError(new ConnectException("Connection refused")));
        
        assertFalse("ConnectException without Connection refused should not be retriable",
                   ErrorHandler.isRetriableError(new ConnectException("Other error")));
        
        assertTrue("IOException with Connection reset should be retriable",
                  ErrorHandler.isRetriableError(new IOException("Connection reset")));
        
        assertTrue("IOException with Broken pipe should be retriable",
                  ErrorHandler.isRetriableError(new IOException("Broken pipe")));
        
        assertFalse("IOException with other message should not be retriable",
                   ErrorHandler.isRetriableError(new IOException("Other IO error")));
        
        assertFalse("Other exceptions should not be retriable",
                   ErrorHandler.isRetriableError(new RuntimeException()));
    }
    
    @Test
    public void testIsExpectedConnectionClose() {
        assertTrue("Connection reset should be expected",
                  ErrorHandler.isExpectedConnectionClose(new IOException("Connection reset")));
        
        assertTrue("Socket closed should be expected",
                  ErrorHandler.isExpectedConnectionClose(new IOException("Socket closed")));
        
        assertTrue("Connection aborted should be expected",
                  ErrorHandler.isExpectedConnectionClose(new IOException("connection was aborted")));
        
        assertFalse("Other IO errors should not be expected connection close",
                   ErrorHandler.isExpectedConnectionClose(new IOException("File not found")));
        
        assertFalse("IO error with null message should not be expected",
                   ErrorHandler.isExpectedConnectionClose(new IOException()));
    }
    
    @Test
    public void testCaseSensitivity() {
        // Test that message matching is case sensitive as expected
        assertTrue("Should handle case variations in exception messages",
                  ErrorHandler.isRetriableError(new IOException("connection reset by peer")));
        
        assertTrue("Should handle case variations",
                  ErrorHandler.isRetriableError(new IOException("Connection Reset")));
    }
}
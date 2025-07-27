package com.comp3331.proxy.utils;

import com.comp3331.proxy.http.HTTPParseException;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.io.IOException;

/**
 * Utility class for consistent error handling across the proxy.
 * Maps exceptions to appropriate HTTP error responses.
 */
public class ErrorHandler {
    
    /**
     * Map an exception to an appropriate HTTP error response.
     * This provides consistent error responses across the proxy.
     */
    public static byte[] mapExceptionToResponse(Throwable error) {
        if (error == null) {
            return ErrorResponseGenerator.badGateway("Unknown error");
        }
        
        // Handle specific exception types
        if (error instanceof ProxyException) {
            return handleProxyException((ProxyException) error);
        } else if (error instanceof HTTPParseException) {
            return ErrorResponseGenerator.badRequest("Malformed HTTP request: " + error.getMessage());
        } else if (error instanceof UnknownHostException) {
            return ErrorResponseGenerator.badGateway("Failed to resolve host");
        } else if (error instanceof SocketTimeoutException) {
            return ErrorResponseGenerator.gatewayTimeout("Connection to origin server timed out");
        } else if (error instanceof ConnectException) {
            return handleConnectException((ConnectException) error);
        } else if (error instanceof IOException) {
            return handleIOException((IOException) error);
        } else {
            // Generic error for unexpected exceptions
            return ErrorResponseGenerator.badGateway("Internal proxy error");
        }
    }
    
    /**
     * Handle ProxyException based on the message content.
     */
    private static byte[] handleProxyException(ProxyException e) {
        String msg = e.getMessage();
        
        if (msg.contains("could not resolve") || msg.contains("could not connect")) {
            return ErrorResponseGenerator.badGateway("Failed to resolve host");
        } else if (msg.contains("timed out")) {
            return ErrorResponseGenerator.gatewayTimeout("Connection timeout");
        } else if (msg.contains("connection refused")) {
            return ErrorResponseGenerator.badGateway("Connection refused by origin server");
        } else if (msg.contains("network unreachable")) {
            return ErrorResponseGenerator.badGateway("Network unreachable");
        } else if (msg.contains("self-loop")) {
            return ErrorResponseGenerator.misdirectedRequest("Self-loop detected");
        } else {
            return ErrorResponseGenerator.badGateway(msg);
        }
    }
    
    /**
     * Handle ConnectException with specific error messages.
     */
    private static byte[] handleConnectException(ConnectException e) {
        String msg = e.getMessage();
        
        if (msg != null && msg.contains("Connection refused")) {
            return ErrorResponseGenerator.badGateway("Connection refused by origin server");
        } else {
            return ErrorResponseGenerator.badGateway("Failed to connect to origin server");
        }
    }
    
    /**
     * Handle IOException with specific error messages.
     */
    private static byte[] handleIOException(IOException e) {
        String msg = e.getMessage();
        
        if (msg != null) {
            if (msg.contains("Network is unreachable")) {
                return ErrorResponseGenerator.badGateway("Network unreachable");
            } else if (msg.contains("Connection reset")) {
                return ErrorResponseGenerator.badGateway("Connection reset by origin server");
            } else if (msg.contains("Broken pipe")) {
                return ErrorResponseGenerator.badGateway("Connection closed unexpectedly");
            }
        }
        
        return ErrorResponseGenerator.badGateway("Network error: " + (msg != null ? msg : "Unknown"));
    }
    
    /**
     * Get HTTP status code for an exception.
     */
    public static int getStatusCode(Throwable error) {
        if (error instanceof HTTPParseException) {
            return 400; // Bad Request
        } else if (error instanceof SocketTimeoutException) {
            return 504; // Gateway Timeout
        } else if (error instanceof ProxyException) {
            String msg = error.getMessage();
            if (msg.contains("self-loop")) {
                return 421; // Misdirected Request
            } else if (msg.contains("timed out")) {
                return 504; // Gateway Timeout
            }
        }
        return 502; // Bad Gateway (default for proxy errors)
    }
    
    /**
     * Check if an error is retriable (for potential retry logic).
     */
    public static boolean isRetriableError(Throwable error) {
        return error instanceof SocketTimeoutException ||
               (error instanceof ConnectException && 
                error.getMessage() != null && 
                error.getMessage().contains("Connection refused")) ||
               (error instanceof IOException && 
                error.getMessage() != null && 
                (error.getMessage().contains("Connection reset") ||
                 error.getMessage().contains("Broken pipe")));
    }
    
    /**
     * Check if an IOException is an expected connection close.
     */
    public static boolean isExpectedConnectionClose(IOException e) {
        String msg = e.getMessage();
        return msg != null && (
            msg.contains("Connection reset") || 
            msg.contains("Socket closed") || 
            msg.contains("connection was aborted")
        );
    }
}

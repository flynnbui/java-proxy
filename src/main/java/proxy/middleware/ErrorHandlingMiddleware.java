package proxy.middleware;

import proxy.utils.ProxyException;
import proxy.utils.ErrorResponseGenerator;
import proxy.http.HTTPParseException;
import proxy.logging.ProxyLogger;

import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import java.io.IOException;

/**
 * Centralized error handling middleware for the proxy.
 * Provides consistent error responses and logging.
 */
public class ErrorHandlingMiddleware {
    private final ProxyLogger logger;
    
    public ErrorHandlingMiddleware(ProxyLogger logger) {
        this.logger = logger;
    }
    
    /**
     * Convert exceptions to appropriate HTTP error responses.
     * This provides a consistent error handling strategy across the proxy.
     */
    public byte[] handleError(Throwable error, String context) {
        // Log the error with context
        logError(error, context);
        
        // Convert to appropriate HTTP response
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
    private byte[] handleProxyException(ProxyException e) {
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
    private byte[] handleConnectException(ConnectException e) {
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
    private byte[] handleIOException(IOException e) {
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
     * Log error with appropriate level and context.
     */
    private void logError(Throwable error, String context) {
        if (error instanceof ProxyException || 
            error instanceof HTTPParseException ||
            error instanceof UnknownHostException ||
            error instanceof SocketTimeoutException) {
            // Expected errors - log as warning
            logger.logWarning(context + ": " + error.getMessage());
        } else {
            // Unexpected errors - log as error with stack trace
            logger.logError(context + ": " + error.getMessage(), error);
        }
    }
    
    /**
     * Check if an error is retriable (for potential retry logic).
     */
    public boolean isRetriableError(Throwable error) {
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
     * Get appropriate HTTP status code for an error.
     */
    public int getStatusCode(Throwable error) {
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
}

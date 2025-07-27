package proxy.utils;

/**
 * Base exception for proxy-related errors.
 */
public class ProxyException extends Exception {
    public ProxyException(String message) {
        super(message);
    }
    
    public ProxyException(String message, Throwable cause) {
        super(message, cause);
    }
}
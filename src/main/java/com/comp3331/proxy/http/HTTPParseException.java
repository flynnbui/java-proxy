package com.comp3331.proxy.http;

/**
 * Exception raised when HTTP parsing fails.
 */
public class HTTPParseException extends Exception {
    public HTTPParseException(String message) {
        super(message);
    }
    
    public HTTPParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
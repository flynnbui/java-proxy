package com.comp3331.proxy.logging;

import java.io.PrintStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Thread-safe logger for HTTP proxy following Common Log Format variant.
 */
public class ProxyLogger {
    private final PrintStream outputStream;
    private final Object lock = new Object();
    
    public ProxyLogger(PrintStream outputStream) {
        this.outputStream = outputStream != null ? outputStream : System.out;
    }
    
    public ProxyLogger() {
        this(System.out);
    }
    
    /**
     * Thread-safe log an HTTP transaction in the required format.
     * 
     * Format: host port cache date request status bytes
     */
    public void logTransaction(String clientIp, int clientPort, String cacheStatus, 
                             String requestLine, int statusCode, int responseBytes) {
        // Default cache_status if not provided
        if (cacheStatus == null) {
            cacheStatus = "-";
        }
        
        synchronized (lock) {
            // Format timestamp as specified: [dd/MMM/yyyy:HH:mm:ss Z]
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.ENGLISH);
            String timestamp = ZonedDateTime.now().format(formatter);
            
            // Build log entry
            String logEntry = String.format("%s %d %s %s \"%s\" %d %d",
                clientIp, clientPort, cacheStatus, timestamp, requestLine, statusCode, responseBytes);
            
            // Write to output stream
            outputStream.println(logEntry);
            outputStream.flush();
        }
    }
    
    /**
     * Log a basic transaction for testing.
     */
    public void logBasicTransaction(String clientIp, int clientPort, String requestLine, 
                                  int statusCode, int responseBytes) {
        logTransaction(clientIp, clientPort, "-", requestLine, statusCode, responseBytes);
    }
    
    /**
     * Log transaction with cache status.
     */
    public void logCacheTransaction(String clientIp, int clientPort, boolean cacheHit, 
                                  String requestLine, int statusCode, int responseBytes) {
        String cacheStatus = cacheHit ? "H" : "M";
        logTransaction(clientIp, clientPort, cacheStatus, requestLine, statusCode, responseBytes);
    }
    
    /**
     * Log warning message.
     */
    public void logWarning(String message) {
        synchronized (lock) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.ENGLISH);
            String timestamp = ZonedDateTime.now().format(formatter);
            System.err.println("[WARN] " + timestamp + " " + message);
        }
    }
    
    /**
     * Log error message with optional exception.
     */
    public void logError(String message, Throwable error) {
        synchronized (lock) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("[dd/MMM/yyyy:HH:mm:ss Z]", Locale.ENGLISH);
            String timestamp = ZonedDateTime.now().format(formatter);
            System.err.println("[ERROR] " + timestamp + " " + message);
            if (error != null) {
                error.printStackTrace(System.err);
            }
        }
    }
}

package proxy.config;

/**
 * Configuration class for the HTTP proxy.
 * Handles command line argument parsing and validation.
 */
public class ProxyConfig {
    private final int port;
    private final int timeout;
    private final int maxObjectSize;
    private final int maxCacheSize;
    
    public ProxyConfig(int port, int timeout, int maxObjectSize, int maxCacheSize) {
        this.port = port;
        this.timeout = timeout;
        this.maxObjectSize = maxObjectSize;
        this.maxCacheSize = maxCacheSize;
    }
    
    /**
     * Create configuration from command line arguments.
     */
    public static ProxyConfig fromArgs(String[] args) throws ConfigException {
        if (args.length != 4) {
            throw new ConfigException("Expected 4 arguments, got " + args.length);
        }
        
        try {
            int port = Integer.parseInt(args[0]);
            int timeout = Integer.parseInt(args[1]);
            int maxObjectSize = Integer.parseInt(args[2]);
            int maxCacheSize = Integer.parseInt(args[3]);
            
            // Validate arguments
            validatePort(port);
            validateTimeout(timeout);
            validateObjectSize(maxObjectSize);
            validateCacheSize(maxCacheSize, maxObjectSize);
            
            return new ProxyConfig(port, timeout, maxObjectSize, maxCacheSize);
            
        } catch (NumberFormatException e) {
            throw new ConfigException("Invalid argument format: " + e.getMessage());
        }
    }
    
    private static void validatePort(int port) throws ConfigException {
        if (port < 1024 || port > 65535) {
            throw new ConfigException("Port must be between 1024 and 65535, got " + port);
        }
        
        // Port validation removed
    }
    
    private static void validateTimeout(int timeout) throws ConfigException {
        if (timeout <= 0) {
            throw new ConfigException("Timeout must be a positive integer, got " + timeout);
        }
    }
    
    private static void validateObjectSize(int maxObjectSize) throws ConfigException {
        if (maxObjectSize <= 0) {
            throw new ConfigException("Max object size must be positive, got " + maxObjectSize);
        }
    }
    
    private static void validateCacheSize(int maxCacheSize, int maxObjectSize) throws ConfigException {
        if (maxCacheSize <= 0) {
            throw new ConfigException("Max cache size must be positive, got " + maxCacheSize);
        }
        
        if (maxCacheSize < maxObjectSize) {
            throw new ConfigException("Max cache size (" + maxCacheSize + 
                ") must be at least equal to max object size (" + maxObjectSize + ")");
        }
    }
    
    // Getters
    public int getPort() { return port; }
    public int getTimeout() { return timeout; }
    public int getMaxObjectSize() { return maxObjectSize; }
    public int getMaxCacheSize() { return maxCacheSize; }
    
    @Override
    public String toString() {
        return String.format("ProxyConfig(port=%d, timeout=%d, maxObjectSize=%d, maxCacheSize=%d)",
            port, timeout, maxObjectSize, maxCacheSize);
    }
}
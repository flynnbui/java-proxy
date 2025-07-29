package proxy;

import proxy.config.ProxyConfig;
import proxy.config.ConfigException;
import proxy.server.ConcurrentProxyServer;

/**
 * COMP3331/9331 HTTP Proxy Implementation
 * Main entry point for the HTTP proxy server.
 * 
 * Usage: java HttpProxy <port> <timeout> <max_object_size> <max_cache_size>
 */
class HttpProxy {
    private static ConcurrentProxyServer proxyServer;
    
    public static void main(String[] args) {
        // Set up shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Received shutdown signal
            if (proxyServer != null) {
                proxyServer.shutdown();
            }
        }));
        
        try {
            // Parse and validate command line arguments
            ProxyConfig config = ProxyConfig.fromArgs(args);
            
            // Create and start concurrent proxy server
            proxyServer = new ConcurrentProxyServer(config, 30);
            
            // Starting HTTP proxy
            
            proxyServer.run();
            
        } catch (ConfigException e) {
            // Configuration error
            System.exit(1);
        } catch (Exception e) {
            // Failed to start proxy server
            e.printStackTrace();
            System.exit(1);
        }
    }
}
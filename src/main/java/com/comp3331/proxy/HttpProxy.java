package com.comp3331.proxy;

import com.comp3331.proxy.config.ProxyConfig;
import com.comp3331.proxy.config.ConfigException;
import com.comp3331.proxy.server.ConcurrentProxyServer;

/**
 * COMP3331/9331 HTTP Proxy Implementation
 * Main entry point for the HTTP proxy server.
 * 
 * Usage: java HttpProxy <port> <timeout> <max_object_size> <max_cache_size>
 */
public class HttpProxy {
    private static ConcurrentProxyServer proxyServer;
    
    public static void main(String[] args) {
        // Set up shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nReceived shutdown signal...");
            if (proxyServer != null) {
                proxyServer.shutdown();
            }
        }));
        
        try {
            // Parse and validate command line arguments
            ProxyConfig config = ProxyConfig.fromArgs(args);
            
            // Create and start concurrent proxy server
            proxyServer = new ConcurrentProxyServer(config, 30);
            
            System.out.println("Starting HTTP proxy (CONCURRENT MODE) on port " + config.getPort());
            System.out.println("Max workers: 30, Timeout: " + config.getTimeout() + "s");
            System.out.println("Max object size: " + config.getMaxObjectSize() + " bytes");
            System.out.println("Max cache size: " + config.getMaxCacheSize() + " bytes");
            
            proxyServer.run();
            
        } catch (ConfigException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: java HttpProxy <port> <timeout> <max_object_size> <max_cache_size>");
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Failed to start proxy server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
package com.comp3331.proxy.utils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Utility for parsing and transforming HTTP request targets.
 */
public class URLParser {
    
    /**
     * Parse absolute-form URL into components.
     * Returns: {scheme, hostname, port, pathWithQuery}
     */
    public static String[] parseAbsoluteUrl(String target) throws ProxyException {
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            throw new ProxyException("Invalid absolute URL: " + target);
        }
        
        try {
            URI uri = new URI(target);
            
            if (uri.getHost() == null) {
                throw new ProxyException("no host");
            }
            
            String scheme = uri.getScheme();
            String hostname = uri.getHost();
            
            // Default ports
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            
            // Build path with query
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getQuery() != null) {
                path += "?" + uri.getQuery();
            }
            
            return new String[]{scheme, hostname, String.valueOf(port), path};
            
        } catch (URISyntaxException e) {
            throw new ProxyException("Invalid URL: " + e.getMessage());
        }
    }
    
    /**
     * Parse authority-form target (for CONNECT).
     * Must be in the form of hostname:port without any scheme.
     * Returns: {hostname, port}
     */
    public static String[] parseAuthorityForm(String target) throws ProxyException {
        // Ensure no scheme is present
        if (target.startsWith("http://") || target.startsWith("https://")) {
            throw new ProxyException("invalid authority form");
        }
        
        if (!target.contains(":")) {
            throw new ProxyException("invalid port");
        }
        
        try {
            int lastColonIndex = target.lastIndexOf(':');
            String hostname = target.substring(0, lastColonIndex);
            String portStr = target.substring(lastColonIndex + 1);
            
            int port = Integer.parseInt(portStr);
            
            if (hostname.isEmpty()) {
                throw new ProxyException("no host");
            }
            
            return new String[]{hostname, String.valueOf(port)};
            
        } catch (NumberFormatException e) {
            throw new ProxyException("invalid port");
        }
    }
    
    /**
     * Check if target points to the proxy itself.
     */
    public static boolean isSelfLoop(String hostname, int port, int proxyPort) {
        // Check if it's localhost/127.0.0.1 and same port
        if (("localhost".equalsIgnoreCase(hostname) || "127.0.0.1".equals(hostname)) && 
            port == proxyPort) {
            return true;
        }
        
        // Could add more sophisticated checks for local IP addresses
        return false;
    }
}
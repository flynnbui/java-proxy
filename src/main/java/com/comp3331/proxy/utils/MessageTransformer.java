package com.comp3331.proxy.utils;

import com.comp3331.proxy.http.HTTPRequest;
import com.comp3331.proxy.http.HTTPResponse;
import com.comp3331.proxy.http.HTTPMessageBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles HTTP message transformation for proxy forwarding.
 */
public class MessageTransformer {
    private final String proxyId;
    
    public MessageTransformer(String proxyId) {
        this.proxyId = proxyId != null ? proxyId : "1.1 z1234567"; // Replace with actual zID
    }
    
    public MessageTransformer() {
        this("1.1 z1234567"); // Default proxy ID
    }
    
    /**
     * Transform client request for forwarding to origin server.
     */
    public byte[] transformRequestForOrigin(HTTPRequest request, String hostname, int port, String path) {
        // Build headers for forwarding
        Map<String, String> headers = new LinkedHashMap<>();
        
        // Copy all headers except proxy-specific ones
        for (Map.Entry<String, String> entry : request.getRawHeaders().entrySet()) {
            String nameLower = entry.getKey().toLowerCase();
            if (!"proxy-connection".equals(nameLower)) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        
        // For origin server, always use Connection: close to simplify proxy logic
        headers.put("Connection", "close");
        
        // Add/append Via header
        String viaValue = proxyId;
        String existingVia = headers.get("Via");
        if (existingVia != null) {
            headers.put("Via", existingVia + ", " + viaValue);
        } else {
            headers.put("Via", viaValue);
        }
        
        // Ensure Host header is correct
        if (port == 80 || port == 443) {
            headers.put("Host", hostname);
        } else {
            headers.put("Host", hostname + ":" + port);
        }
        
        // Build the request with origin-form target
        return HTTPMessageBuilder.buildRequest(request.getMethod(), path, request.getVersion(),
                                             headers, request.getBody());
    }
    
    /**
     * Transform origin server response for forwarding to client.
     */
    public byte[] transformResponseForClient(HTTPResponse response) {
        return transformResponseForClient(response, null);
    }
    
    /**
     * Transform origin server response for forwarding to client with connection preference.
     */
    public byte[] transformResponseForClient(HTTPResponse response, HTTPRequest clientRequest) {
        // Build headers for forwarding
        Map<String, String> headers = new LinkedHashMap<>();
        
        // Copy all headers except Connection (we'll set it based on client preference)
        for (Map.Entry<String, String> entry : response.getRawHeaders().entrySet()) {
            if (!"connection".equalsIgnoreCase(entry.getKey())) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        
        // Set Connection header based on client request
        if (clientRequest != null) {
            String clientConnection = clientRequest.getHeader("Connection");
            if ("keep-alive".equalsIgnoreCase(clientConnection)) {
                headers.put("Connection", "keep-alive");
            } else if ("close".equalsIgnoreCase(clientConnection)) {
                headers.put("Connection", "close");
            } else {
                // HTTP/1.1 defaults to keep-alive, HTTP/1.0 defaults to close
                if ("HTTP/1.1".equals(clientRequest.getVersion())) {
                    headers.put("Connection", "keep-alive");
                } else {
                    headers.put("Connection", "close");
                }
            }
        } else {
            // No client request info, default to close
            headers.put("Connection", "close");
        }
        
        // Add/append Via header
        String viaValue = proxyId;
        String existingVia = headers.get("Via");
        if (existingVia != null) {
            headers.put("Via", existingVia + ", " + viaValue);
        } else {
            headers.put("Via", viaValue);
        }
        
        return HTTPMessageBuilder.buildResponse(response.getVersion(), response.getStatusCode(), 
                                              response.getReasonPhrase(), headers, response.getBody());
    }
}
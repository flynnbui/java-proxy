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
        
        // Set Connection: close
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
        // Build headers for forwarding
        Map<String, String> headers = new LinkedHashMap<>();
        
        // Copy all headers
        for (Map.Entry<String, String> entry : response.getRawHeaders().entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }
        
        // Set Connection: close
        headers.put("Connection", "close");
        
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
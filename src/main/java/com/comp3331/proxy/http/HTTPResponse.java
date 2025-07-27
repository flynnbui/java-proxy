package com.comp3331.proxy.http;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an HTTP response message.
 */
public class HTTPResponse {
    private String version;
    private int statusCode;
    private String reasonPhrase;
    private Map<String, String> headers; // Case-insensitive headers
    private byte[] body;
    private Map<String, String> rawHeaders; // Original case headers for forwarding
    
    public HTTPResponse() {
        this.headers = new HashMap<>();
        this.rawHeaders = new LinkedHashMap<>();
        this.body = new byte[0];
    }
    
    /**
     * Get header value (case-insensitive).
     */
    public String getHeader(String name) {
        return headers.get(name.toLowerCase());
    }
    
    /**
     * Set header value (preserves original case).
     */
    public void setHeader(String name, String value) {
        headers.put(name.toLowerCase(), value);
        rawHeaders.put(name, value);
    }
    
    /**
     * Check if response has a message body based on assignment rules.
     */
    public boolean hasBody(String requestMethod) {
        // HEAD requests never have body
        if ("HEAD".equals(requestMethod)) {
            return false;
        }
        
        // 204 No Content and 304 Not Modified never have body
        if (statusCode == 204 || statusCode == 304) {
            return false;
        }
        
        // Transfer-Encoding or Content-Length indicates body
        return getHeader("transfer-encoding") != null || getHeader("content-length") != null;
    }
    
    /**
     * Get content length as integer, or null if not present.
     */
    public Integer getContentLength() throws HTTPParseException {
        String length = getHeader("content-length");
        if (length != null) {
            try {
                return Integer.parseInt(length);
            } catch (NumberFormatException e) {
                throw new HTTPParseException("Invalid Content-Length: " + length);
            }
        }
        return null;
    }
    
    /**
     * Check if response uses transfer encoding.
     */
    public boolean hasTransferEncoding() {
        return getHeader("transfer-encoding") != null;
    }
    
    // Getters and setters
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    
    public String getReasonPhrase() { return reasonPhrase; }
    public void setReasonPhrase(String reasonPhrase) { this.reasonPhrase = reasonPhrase; }
    
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getRawHeaders() { return rawHeaders; }
    
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body != null ? body : new byte[0]; }
    
    @Override
    public String toString() {
        return String.format("HTTPResponse(version=%s, statusCode=%d, reasonPhrase=%s)", 
            version, statusCode, reasonPhrase);
    }
}
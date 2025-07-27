package com.comp3331.proxy.http;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents an HTTP request message.
 */
public class HTTPRequest {
    private String method;
    private String target;
    private String version;
    private Map<String, String> headers; // Case-insensitive headers
    private byte[] body;
    private Map<String, String> rawHeaders; // Original case headers for forwarding
    
    public HTTPRequest() {
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
     * Check if request has a message body.
     */
    public boolean hasBody() {
        // Requests have body if Content-Length is present
        return getHeader("content-length") != null;
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
     * Check if request uses transfer encoding.
     */
    public boolean hasTransferEncoding() {
        return getHeader("transfer-encoding") != null;
    }
    
    // Getters and setters
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public Map<String, String> getHeaders() { return headers; }
    public Map<String, String> getRawHeaders() { return rawHeaders; }
    
    public byte[] getBody() { return body; }
    public void setBody(byte[] body) { this.body = body != null ? body : new byte[0]; }
    
    @Override
    public String toString() {
        return String.format("HTTPRequest(method=%s, target=%s, version=%s)", method, target, version);
    }
}
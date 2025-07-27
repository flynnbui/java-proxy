package com.comp3331.proxy.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Utility for building HTTP messages for sending.
 */
public class HTTPMessageBuilder {
    
    /**
     * Build HTTP request message.
     */
    public static byte[] buildRequest(String method, String target, String version, 
                                    Map<String, String> headers, byte[] body) {
        StringBuilder message = new StringBuilder();
        message.append(method).append(" ").append(target).append(" ").append(version).append("\r\n");
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                message.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }
        
        message.append("\r\n");
        
        byte[] headerBytes = message.toString().getBytes(StandardCharsets.UTF_8);
        
        if (body != null && body.length > 0) {
            byte[] result = new byte[headerBytes.length + body.length];
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
            System.arraycopy(body, 0, result, headerBytes.length, body.length);
            return result;
        } else {
            return headerBytes;
        }
    }
    
    /**
     * Build HTTP response message.
     */
    public static byte[] buildResponse(String version, int statusCode, String reasonPhrase,
                                     Map<String, String> headers, byte[] body) {
        StringBuilder message = new StringBuilder();
        message.append(version).append(" ").append(statusCode).append(" ").append(reasonPhrase).append("\r\n");
        
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                message.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }
        
        message.append("\r\n");
        
        byte[] headerBytes = message.toString().getBytes(StandardCharsets.UTF_8);
        
        if (body != null && body.length > 0) {
            byte[] result = new byte[headerBytes.length + body.length];
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
            System.arraycopy(body, 0, result, headerBytes.length, body.length);
            return result;
        } else {
            return headerBytes;
        }
    }
    
    /**
     * Build HTTP request from HTTPRequest object.
     */
    public static byte[] buildRequest(HTTPRequest request) {
        return buildRequest(request.getMethod(), request.getTarget(), request.getVersion(),
                          request.getRawHeaders(), request.getBody());
    }
    
    /**
     * Build HTTP response from HTTPResponse object.
     */
    public static byte[] buildResponse(HTTPResponse response) {
        return buildResponse(response.getVersion(), response.getStatusCode(), response.getReasonPhrase(),
                           response.getRawHeaders(), response.getBody());
    }
}
package proxy.utils;

import proxy.http.HTTPMessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates HTTP error responses.
 */
public class ErrorResponseGenerator {
    
    /**
     * Generate an HTTP error response.
     */
    public static byte[] generateErrorResponse(int statusCode, String errorMessage, String contentType) {
        Map<Integer, String> reasonPhrases = new HashMap<>();
        reasonPhrases.put(400, "Bad Request");
        reasonPhrases.put(421, "Misdirected Request");
        reasonPhrases.put(502, "Bad Gateway");
        reasonPhrases.put(504, "Gateway Timeout");
        
        String reasonPhrase = reasonPhrases.getOrDefault(statusCode, "Error");
        
        // Create simple error page
        String body = String.format("Error %d: %s\n\n%s", statusCode, reasonPhrase, errorMessage);
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        headers.put("Content-Length", String.valueOf(bodyBytes.length));
        headers.put("Connection", "close");
        
        return HTTPMessageBuilder.buildResponse("HTTP/1.1", statusCode, reasonPhrase, headers, bodyBytes);
    }
    
    public static byte[] generateErrorResponse(int statusCode, String errorMessage) {
        return generateErrorResponse(statusCode, errorMessage, "text/plain");
    }
    
    /**
     * Generate 400 Bad Request response.
     */
    public static byte[] badRequest(String message) {
        return generateErrorResponse(400, message);
    }
    
    /**
     * Generate 421 Misdirected Request response.
     */
    public static byte[] misdirectedRequest(String message) {
        return generateErrorResponse(421, message);
    }
    
    /**
     * Generate 502 Bad Gateway response.
     */
    public static byte[] badGateway(String message) {
        return generateErrorResponse(502, message);
    }
    
    /**
     * Generate 504 Gateway Timeout response.
     */
    public static byte[] gatewayTimeout(String message) {
        return generateErrorResponse(504, message);
    }
}
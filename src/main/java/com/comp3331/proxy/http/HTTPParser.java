package com.comp3331.proxy.http;

import java.util.regex.Pattern;

/**
 * HTTP message parser for requests and responses.
 */
public class HTTPParser {
    private static final Pattern METHOD_PATTERN = Pattern.compile("^[A-Z]+$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^HTTP/\\d+\\.\\d+$");
    private static final Pattern HEADER_NAME_PATTERN = Pattern.compile("^[!#$%&'*+\\-.0-9A-Z^_`a-z|~]+$");
    
    /**
     * Parse HTTP request line into method, target, version.
     */
    public static String[] parseRequestLine(String line) throws HTTPParseException {
        String[] parts = line.trim().split(" ", 3);
        if (parts.length != 3) {
            throw new HTTPParseException("Invalid request line: " + line);
        }
        
        String method = parts[0];
        String target = parts[1];
        String version = parts[2];
        
        // Validate method
        if (!METHOD_PATTERN.matcher(method).matches()) {
            throw new HTTPParseException("Invalid method: " + method);
        }
        
        // Validate version
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new HTTPParseException("Invalid HTTP version: " + version);
        }
        
        return new String[]{method, target, version};
    }
    
    /**
     * Parse HTTP status line into version, status code, reason phrase.
     */
    public static Object[] parseStatusLine(String line) throws HTTPParseException {
        String[] parts = line.trim().split(" ", 3);
        if (parts.length < 2) {
            throw new HTTPParseException("Invalid status line: " + line);
        }
        
        String version = parts[0];
        int statusCode;
        try {
            statusCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new HTTPParseException("Invalid status code: " + parts[1]);
        }
        
        String reasonPhrase = parts.length > 2 ? parts[2] : "";
        
        // Validate version
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new HTTPParseException("Invalid HTTP version: " + version);
        }
        
        // Validate status code range
        if (statusCode < 100 || statusCode > 599) {
            throw new HTTPParseException("Status code out of range: " + statusCode);
        }
        
        return new Object[]{version, statusCode, reasonPhrase};
    }
    
    /**
     * Parse a single header line into name and value.
     */
    public static String[] parseHeaderLine(String line) throws HTTPParseException {
        int colonIndex = line.indexOf(':');
        if (colonIndex == -1) {
            throw new HTTPParseException("Invalid header line: " + line);
        }
        
        String name = line.substring(0, colonIndex).trim();
        String value = line.substring(colonIndex + 1).trim();
        
        if (name.isEmpty()) {
            throw new HTTPParseException("Empty header name");
        }
        
        // Validate header name (RFC 7230)
        if (!HEADER_NAME_PATTERN.matcher(name).matches()) {
            throw new HTTPParseException("Invalid header name: " + name);
        }
        
        return new String[]{name, value};
    }
    
    /**
     * Parse HTTP request from raw data.
     */
    public static HTTPRequest parseRequest(String data) throws HTTPParseException {
        if (data == null || data.isEmpty()) {
            throw new HTTPParseException("Empty request");
        }
        
        // Split into lines
        String[] lines = data.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        
        if (lines.length == 0) {
            throw new HTTPParseException("Empty request");
        }
        
        HTTPRequest request = new HTTPRequest();
        
        // Parse request line
        String[] requestParts = parseRequestLine(lines[0]);
        request.setMethod(requestParts[0]);
        request.setTarget(requestParts[1]);
        request.setVersion(requestParts[2]);
        
        // Find end of headers (empty line)
        int headerEnd = 1;
        while (headerEnd < lines.length && !lines[headerEnd].trim().isEmpty()) {
            headerEnd++;
        }
        
        // Parse headers
        if (headerEnd > 1) {
            for (int i = 1; i < headerEnd; i++) {
                String[] headerParts = parseHeaderLine(lines[i]);
                request.setHeader(headerParts[0], headerParts[1]);
            }
        }
        
        // Parse body if present (body comes after empty line)
        if (headerEnd < lines.length - 1) {
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = headerEnd + 1; i < lines.length; i++) {
                if (i > headerEnd + 1) {
                    bodyBuilder.append("\n");
                }
                bodyBuilder.append(lines[i]);
            }
            request.setBody(bodyBuilder.toString().getBytes());
        }
        
        return request;
    }
    
    /**
     * Parse HTTP response from raw data.
     */
    public static HTTPResponse parseResponse(String data) throws HTTPParseException {
        if (data == null || data.isEmpty()) {
            throw new HTTPParseException("Empty response");
        }
        
        // Split into lines
        String[] lines = data.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        
        if (lines.length == 0) {
            throw new HTTPParseException("Empty response");
        }
        
        HTTPResponse response = new HTTPResponse();
        
        // Parse status line
        Object[] statusParts = parseStatusLine(lines[0]);
        response.setVersion((String) statusParts[0]);
        response.setStatusCode((Integer) statusParts[1]);
        response.setReasonPhrase((String) statusParts[2]);
        
        // Find end of headers (empty line)
        int headerEnd = 1;
        while (headerEnd < lines.length && !lines[headerEnd].trim().isEmpty()) {
            headerEnd++;
        }
        
        // Parse headers
        if (headerEnd > 1) {
            for (int i = 1; i < headerEnd; i++) {
                String[] headerParts = parseHeaderLine(lines[i]);
                response.setHeader(headerParts[0], headerParts[1]);
            }
        }
        
        // Parse body if present
        if (headerEnd < lines.length - 1) {
            StringBuilder bodyBuilder = new StringBuilder();
            for (int i = headerEnd + 1; i < lines.length; i++) {
                if (i > headerEnd + 1) {
                    bodyBuilder.append("\n");
                }
                bodyBuilder.append(lines[i]);
            }
            response.setBody(bodyBuilder.toString().getBytes());
        }
        
        return response;
    }
}
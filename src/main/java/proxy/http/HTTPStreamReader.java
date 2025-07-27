package proxy.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Handles reading and parsing HTTP messages from socket streams.
 */
public class HTTPStreamReader {
    private final Socket socket;
    private final InputStream inputStream;
    private final int timeout;
    private byte[] buffer;
    private int bufferPos;
    private int bufferLimit;
    private static final int MAX_HEADER_SIZE = 65536; // 64KB max for headers
    private static final int BUFFER_SIZE = 4096;
    
    public HTTPStreamReader(Socket socket, int timeout) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.timeout = timeout;
        this.buffer = new byte[BUFFER_SIZE];
        this.bufferPos = 0;
        this.bufferLimit = 0;
        
        if (timeout > 0) {
            socket.setSoTimeout(timeout * 1000); // Convert to milliseconds
        }
    }
    
    /**
     * Read and parse complete HTTP message from socket.
     */
    public HTTPRequest readHttpRequest() throws HTTPParseException, IOException {
        // Read headers first
        byte[] headersData = readHeaders();
        
        // Parse the headers part
        HTTPRequest request = HTTPParser.parseRequest(new String(headersData, "UTF-8"));
        
        // Read body if needed
        if (request.hasBody()) {
            Integer contentLength = request.getContentLength();
            if (contentLength != null && contentLength > 0) {
                byte[] bodyData = readExactBytes(contentLength);
                request.setBody(bodyData);
            }
        }
        
        return request;
    }
    
    /**
     * Read HTTP response message.
     */
    public HTTPResponse readHttpResponse(String requestMethod) throws HTTPParseException, IOException {
        // Read headers first
        byte[] headersData = readHeaders();
        
        // Parse the headers part
        HTTPResponse response = HTTPParser.parseResponse(new String(headersData, "UTF-8"));
        
        // Read body if needed
        if ("HEAD".equals(requestMethod)) {
            // HEAD responses never have a body
        } else if (response.getStatusCode() == 204 || response.getStatusCode() == 304) {
            // No body for these status codes
        } else if (response.hasTransferEncoding()) {
            // Handle transfer encoding
            String transferEncoding = response.getHeader("Transfer-Encoding");
            if ("chunked".equalsIgnoreCase(transferEncoding)) {
                // For simplicity, read until connection close
                byte[] bodyData = readUntilClose();
                response.setBody(bodyData);
            } else {
                // Unknown transfer encoding, assume no body
                response.setBody(new byte[0]);
            }
        } else {
            Integer contentLength = response.getContentLength();
            if (contentLength != null && contentLength > 0) {
                byte[] bodyData = readExactBytes(contentLength);
                response.setBody(bodyData);
            } else if (contentLength == null && "close".equalsIgnoreCase(response.getHeader("Connection"))) {
                // No content length but Connection: close, read until EOF
                byte[] bodyData = readUntilClose();
                response.setBody(bodyData);
            } else {
                // No content length and no indication of body
                response.setBody(new byte[0]);
            }
        }
        
        return response;
    }
    
    /**
     * Read HTTP headers until empty line is found.
     */
    private byte[] readHeaders() throws HTTPParseException, IOException {
        ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
        boolean foundEnd = false;
        int totalRead = 0;
        
        while (!foundEnd && totalRead < MAX_HEADER_SIZE) {
            int b = readByte();
            if (b == -1) {
                break; // Connection closed
            }
            
            headerBuffer.write(b);
            totalRead++;
            
            // Check for end of headers (\r\n\r\n or \n\n)
            byte[] current = headerBuffer.toByteArray();
            if (current.length >= 4) {
                int len = current.length;
                if ((current[len-4] == '\r' && current[len-3] == '\n' && 
                     current[len-2] == '\r' && current[len-1] == '\n') ||
                    (current[len-2] == '\n' && current[len-1] == '\n')) {
                    foundEnd = true;
                }
            }
        }
        
        if (!foundEnd && totalRead >= MAX_HEADER_SIZE) {
            throw new HTTPParseException("Headers too large");
        }
        
        return headerBuffer.toByteArray();
    }
    
    /**
     * Read exactly num_bytes from the socket.
     */
    private byte[] readExactBytes(int numBytes) throws HTTPParseException, IOException {
        byte[] data = new byte[numBytes];
        int totalRead = 0;
        
        while (totalRead < numBytes) {
            int bytesRead = readBytes(data, totalRead, numBytes - totalRead);
            if (bytesRead == -1) {
                throw new HTTPParseException("Expected " + numBytes + " bytes, got " + totalRead);
            }
            totalRead += bytesRead;
        }
        
        return data;
    }
    
    /**
     * Read all remaining data until connection closes.
     */
    private byte[] readUntilClose() throws IOException {
        ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
        byte[] tempBuffer = new byte[BUFFER_SIZE];
        
        // Save original timeout and set a shorter one for reading chunks
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(2000); // 2 second timeout for chunked reads
            
            int bytesRead;
            while ((bytesRead = inputStream.read(tempBuffer)) != -1) {
                bodyBuffer.write(tempBuffer, 0, bytesRead);
            }
        } catch (SocketTimeoutException e) {
            // Connection timeout - return what we have
        } finally {
            // Restore original timeout
            socket.setSoTimeout(originalTimeout);
        }
        
        return bodyBuffer.toByteArray();
    }
    
    /**
     * Read a single byte from the stream.
     */
    private int readByte() throws IOException {
        if (bufferPos >= bufferLimit) {
            fillBuffer();
            if (bufferLimit == 0) {
                return -1; // EOF
            }
        }
        return buffer[bufferPos++] & 0xFF;
    }
    
    /**
     * Read bytes into provided array.
     */
    private int readBytes(byte[] dest, int offset, int length) throws IOException {
        if (bufferPos >= bufferLimit) {
            fillBuffer();
            if (bufferLimit == 0) {
                return -1; // EOF
            }
        }
        
        int available = bufferLimit - bufferPos;
        int toRead = Math.min(length, available);
        System.arraycopy(buffer, bufferPos, dest, offset, toRead);
        bufferPos += toRead;
        
        return toRead;
    }
    
    /**
     * Fill internal buffer from input stream.
     */
    private void fillBuffer() throws IOException {
        bufferPos = 0;
        bufferLimit = inputStream.read(buffer);
        if (bufferLimit == -1) {
            bufferLimit = 0;
        }
    }
}
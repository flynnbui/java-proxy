package proxy.server;

import proxy.config.ProxyConfig;
import proxy.http.*;
import proxy.utils.*;
import proxy.logging.ProxyLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * HTTP proxy server implementation.
 */
public class ProxyServer {
    protected final ProxyConfig config;
    protected ServerSocket serverSocket;
    protected volatile boolean running;
    protected final MessageTransformer transformer;
    protected final OriginConnector connector;
    protected final ProxyLogger logger;
    
    public ProxyServer(ProxyConfig config) {
        this.config = config;
        this.running = false;
        this.transformer = new MessageTransformer();
        this.connector = new OriginConnector(config.getTimeout());
        this.logger = new ProxyLogger();
    }
    
    /**
     * Start the proxy server and begin accepting connections.
     */
    public void run() throws IOException {
        setupServerSocket();
        running = true;
        
        try {
            mainLoop();
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }
    
    /**
     * Set up the main server socket.
     */
    protected void setupServerSocket() throws IOException {
        try {
            serverSocket = new ServerSocket(config.getPort());
            serverSocket.setReuseAddress(true);
            
            System.out.println("Proxy server listening on port " + config.getPort());
            
        } catch (IOException e) {
            throw new IOException("Failed to setup server socket: " + e.getMessage(), e);
        }
    }
    
    /**
     * Main event loop for accepting and handling client connections.
     */
    protected void mainLoop() {
        while (running) {
            try {
                if (serverSocket != null) {
                    Socket clientSocket = serverSocket.accept();
                    String clientIp = clientSocket.getInetAddress().getHostAddress();
                    int clientPort = clientSocket.getPort();
                    
                    System.out.println("Accepted connection from " + clientIp + ":" + clientPort);
                    
                    // Handle HTTP proxy request
                    handleClientRequest(clientSocket, clientIp, clientPort);
                }
            } catch (SocketException e) {
                if (running) {
                    System.err.println("Socket error: " + e.getMessage());
                }
                break;
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            } catch (Exception e) {
                System.err.println("Unexpected error in main loop: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Handle HTTP proxy request from client.
     */
    protected void handleClientRequest(Socket clientSocket, String clientIp, int clientPort) {
        String requestLine = "";
        int statusCode = 502; // Default to Bad Gateway
        int responseBytes = 0;
        
        try {
            // Set socket timeout
            clientSocket.setSoTimeout(config.getTimeout() * 1000);
            
            // Read and parse HTTP request
            HTTPStreamReader reader = new HTTPStreamReader(clientSocket, config.getTimeout());
            HTTPRequest request = reader.readHttpRequest();
            requestLine = request.getMethod() + " " + request.getTarget() + " " + request.getVersion();
            
            System.out.println("Request: " + requestLine);
            
            // Handle different HTTP methods
            byte[] responseData;
            if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()) || "POST".equals(request.getMethod())) {
                responseData = handleHttpMethod(request, clientIp, clientPort);
                statusCode = extractStatusCode(responseData);
                responseBytes = extractResponseBodySize(responseData);
            } else if ("CONNECT".equals(request.getMethod())) {
                responseData = handleConnectMethod(request, clientSocket);
                statusCode = extractStatusCode(responseData);
                responseBytes = 0; // CONNECT responses have no body
            } else {
                // Unsupported method
                responseData = ErrorResponseGenerator.badRequest("Method not supported: " + request.getMethod());
                statusCode = 400;
                responseBytes = responseData.length;
            }
            
            // Send response to client
            if (responseData != null) {
                OutputStream clientOutput = clientSocket.getOutputStream();
                clientOutput.write(responseData);
                clientOutput.flush();
            }
            
        } catch (HTTPParseException e) {
            // Malformed request
            try {
                byte[] errorResponse = ErrorResponseGenerator.badRequest("Malformed request: " + e.getMessage());
                clientSocket.getOutputStream().write(errorResponse);
                statusCode = 400;
                responseBytes = errorResponse.length;
            } catch (IOException ioE) {
                System.err.println("Failed to send error response: " + ioE.getMessage());
            }
        } catch (ProxyException e) {
            // Proxy specific errors - use ErrorHandler
            try {
                byte[] errorResponse = ErrorHandler.mapExceptionToResponse(e);
                statusCode = ErrorHandler.getStatusCode(e);
                clientSocket.getOutputStream().write(errorResponse);
                responseBytes = errorResponse.length;
            } catch (IOException ioE) {
                System.err.println("Failed to send error response: " + ioE.getMessage());
            }
        } catch (SocketTimeoutException e) {
            // Client timeout
            try {
                byte[] errorResponse = ErrorResponseGenerator.gatewayTimeout("Request timeout");
                clientSocket.getOutputStream().write(errorResponse);
                statusCode = 504;
                responseBytes = errorResponse.length;
            } catch (IOException ioE) {
                System.err.println("Failed to send timeout response: " + ioE.getMessage());
            }
        } catch (Exception e) {
            System.err.println("Error handling client request: " + e.getMessage());
            e.printStackTrace();
            try {
                byte[] errorResponse = ErrorResponseGenerator.badGateway("Internal proxy error");
                clientSocket.getOutputStream().write(errorResponse);
                statusCode = 502;
                responseBytes = errorResponse.length;
            } catch (IOException ioE) {
                System.err.println("Failed to send error response: " + ioE.getMessage());
            }
        } finally {
            // Log the transaction
            logger.logTransaction(clientIp, clientPort, "-", requestLine, statusCode, responseBytes);
            
            // Close client socket
            closeSocketSafely(clientSocket);
        }
    }
    
    /**
     * Handle GET, HEAD, POST methods.
     */
    protected byte[] handleHttpMethod(HTTPRequest request, String clientIp, int clientPort) throws ProxyException, IOException, HTTPParseException {
        try {
            // Parse target URL
            String[] urlParts = URLParser.parseAbsoluteUrl(request.getTarget());
            String scheme = urlParts[0];
            String hostname = urlParts[1];
            int port = Integer.parseInt(urlParts[2]);
            String path = urlParts[3];
            
            // Check for self-loop
            if (URLParser.isSelfLoop(hostname, port, config.getPort())) {
                return ErrorResponseGenerator.misdirectedRequest("Self-loop detected");
            }
            
            // Connect to origin server
            Socket originSocket = connector.connectToOrigin(hostname, port);
            
            try {
                // Transform and send request to origin
                byte[] transformedRequest = transformer.transformRequestForOrigin(request, hostname, port, path);
                originSocket.getOutputStream().write(transformedRequest);
                originSocket.getOutputStream().flush();
                
                // Read response from origin
                HTTPStreamReader originReader = new HTTPStreamReader(originSocket, config.getTimeout());
                HTTPResponse response = originReader.readHttpResponse(request.getMethod());
                
                // Transform response for client with connection preference
                return transformer.transformResponseForClient(response, request);
                
            } finally {
                closeSocketSafely(originSocket);
            }
        } catch (ProxyException e) {
            // Re-throw to be handled by caller
            throw e;
        }
    }
    
    /**
     * Handle CONNECT method (placeholder - to be implemented in subclass).
     */
    protected byte[] handleConnectMethod(HTTPRequest request, Socket clientSocket) throws ProxyException {
        return ErrorResponseGenerator.badRequest("CONNECT method not implemented in base server");
    }
    
    /**
     * Extract status code from HTTP response data.
     */
    protected int extractStatusCode(byte[] responseData) {
        if (responseData == null || responseData.length < 12) {
            return 502;
        }
        
        try {
            String responseStr = new String(responseData, 0, Math.min(responseData.length, 100));
            String[] lines = responseStr.split("\\r?\\n");
            if (lines.length > 0) {
                String statusLine = lines[0];
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (NumberFormatException e) {
            // Ignore parsing errors
        }
        
        return 502;
    }
    
    /**
     * Extract response body size for logging.
     */
    protected int extractResponseBodySize(byte[] responseData) {
        if (responseData == null) {
            return 0;
        }
        
        // Find end of headers
        String responseStr = new String(responseData);
        int headerEnd = responseStr.indexOf("\r\n\r\n");
        if (headerEnd == -1) {
            headerEnd = responseStr.indexOf("\n\n");
            if (headerEnd == -1) {
                return 0;
            }
            headerEnd += 2;
        } else {
            headerEnd += 4;
        }
        
        return Math.max(0, responseData.length - headerEnd);
    }
    
    /**
     * Safely close socket without throwing exceptions.
     */
    protected void closeSocketSafely(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Shutdown the proxy server.
     */
    public void shutdown() {
        System.out.println("Shutting down proxy server...");
        running = false;
        
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing server socket: " + e.getMessage());
            }
        }
    }
    
    /**
     * Clean up resources.
     */
    protected void cleanup() {
        shutdown();
        System.out.println("Proxy server shutdown complete");
    }
}
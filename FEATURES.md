# Java HTTP Proxy - Feature Implementation

## Architecture Overview
- **Package Structure**: Logical organization under `com.comp3331.proxy.*`
- **Core Components**: Config, HTTP parsing, utilities, caching, logging, server
- **Concurrency**: ThreadPoolExecutor with configurable worker threads (default: 30)
- **Socket Programming**: Pure Java socket API, no external HTTP libraries

## Implemented Features (20 marks total)

### ✅ Basic Non-Persistent Proxy (4 marks)
- **GET Method**: Full support with absolute-form URL parsing
- **HEAD Method**: Proper handling with no response body
- **POST Method**: Request body forwarding support
- **HTTP Transformation**: absolute-form → origin-form, Connection: close, Via headers

### ✅ Basic Persistent Proxy (3 marks)
- **Connection Persistence**: Multiple requests per connection
- **Connection Management**: HTTP/1.0 vs HTTP/1.1 behavior
- **Header Processing**: Connection and Proxy-Connection headers
- **Timeout Handling**: Configurable idle timeouts

### ✅ Via Header (1 mark)
- **Request Via**: Added to outgoing requests to origin servers
- **Response Via**: Added to responses sent back to clients
- **Header Chaining**: Proper comma-separated chaining for multiple proxies

### ✅ CONNECT Method (2 marks)
- **HTTPS Tunneling**: Bidirectional raw data relay using streams
- **Port Restriction**: Only port 443 allowed (HTTPS)
- **Tunnel Establishment**: 200 Connection Established response
- **Self-loop Prevention**: Detects and blocks connections to proxy itself

### ✅ Error Handling (2 marks)
- **400 Bad Request**: Malformed HTTP requests, invalid ports
- **421 Misdirected Request**: Self-loop detection for all methods
- **502 Bad Gateway**: DNS failures, connection refused, server errors
- **504 Gateway Timeout**: Connection and read timeouts

### ✅ Logging (1 mark)
- **Common Log Format**: host port cache [timestamp] "request" status bytes
- **Thread Safety**: Synchronized logging for concurrent access
- **Cache Status**: H (hit), M (miss), - (non-cacheable)
- **Timestamp Format**: [dd/MMM/yyyy:HH:mm:ss Z]

### ✅ Caching (2 marks)
- **LRU Eviction**: LinkedHashMap with access-order for LRU behavior
- **Size Management**: max_object_size and max_cache_size limits
- **Selective Caching**: Only GET requests with 200 OK responses
- **URL Normalization**: Case-insensitive scheme/host, default ports
- **Thread Safety**: ReentrantReadWriteLock for concurrent access

### ✅ Concurrency (2 marks)
- **Persistent Connections**: Thread-safe persistent connection handling
- **Thread Pool**: ExecutorService with configurable worker threads
- **Connection Statistics**: Total, active, completed connection tracking
- **Resource Management**: Proper socket cleanup and thread termination

### ✅ Additional Quality Features
- **Comprehensive Build System**: Make-based compilation and execution
- **Exception Hierarchy**: Structured error handling with custom exceptions
- **Memory Management**: Efficient buffer management and resource cleanup
- **Configuration Validation**: Port range, size limit, and dependency checks

## Technical Implementation

### Socket Programming
- **Pure Java Sockets**: No external HTTP libraries, only basic socket API
- **Stream Management**: Buffered input/output with timeout handling
- **Connection Pooling**: Thread pool for concurrent connection handling

### HTTP Protocol Support
- **HTTP/1.0 & HTTP/1.1**: Proper version-specific behavior
- **Message Parsing**: RFC-compliant header and body parsing
- **Content-Length**: Proper body size determination and handling
- **Keep-Alive**: Connection persistence based on protocol version

### Architecture Design
- **Modular Design**: Clear separation of concerns across packages
- **Thread Safety**: All shared resources protected with appropriate locks
- **Extensibility**: Base classes allow for easy feature additions
- **Error Recovery**: Graceful handling of malformed requests and network errors

## Build and Usage

```bash
# Compile the project
make build

# Run the proxy server
make run ARGS='<port> <timeout> <max_object_size> <max_cache_size>'

# Example usage
make run ARGS='50000 10 1048576 10485760'
```

## Testing Compatibility
- **CSE Environment**: Compatible with standard Java runtime
- **Command Line**: Supports all required command line arguments
- **Protocol Compliance**: Handles real HTTP clients and servers
- **Stress Testing**: Concurrent connection handling under load
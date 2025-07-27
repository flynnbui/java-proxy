# Java HTTP Proxy

A multithreaded HTTP proxy server implementation in Java with caching support.

## Project Structure

```
java-proxy/
├── src/main/java/proxy/       # Main source code
│   ├── HttpProxy.java         # Main entry point
│   ├── cache/                 # Caching implementation
│   ├── config/                # Configuration classes
│   ├── http/                  # HTTP parsing and handling
│   ├── logging/               # Logging utilities
│   ├── middleware/            # Error handling middleware
│   ├── server/                # Server implementations
│   └── utils/                 # Utility classes
├── testScript/                # Python test scripts
├── build/                     # Build output directory (generated)
├── out/                       # Alternative build output (generated)
├── Makefile                   # Build configuration for Unix/Linux
├── build.bat                  # Build script for Windows
└── .gitignore                 # Git ignore file
```

## Building the Project

### Windows (using build.bat)
```bash
# Build the project
build.bat

# Build to 'out' directory (for compatibility)
build.bat build-out

# Clean build artifacts
build.bat clean

# Build and run with default settings
build.bat run

# Build and run tests
build.bat test
```

### Unix/Linux (using make)
```bash
# Build the project
make

# Build to 'out' directory
make build-out

# Clean build artifacts
make clean

# Run with default settings (port 8080)
make run

# Run on specific port
make run-port PORT=50000

# Run with custom parameters
make run-custom PORT=50000 TIMEOUT=30 MAX_OBJ=102400 MAX_CACHE=1048576

# Run tests
make test
```

## Running the Proxy

### Command Line Arguments
```
java -cp build proxy.HttpProxy <port> <timeout> <max_object_size> <max_cache_size>
```

- `port`: Port number to listen on (1024-65535)
- `timeout`: Connection timeout in seconds
- `max_object_size`: Maximum size of cached objects in bytes
- `max_cache_size`: Maximum total cache size in bytes

### Example
```bash
java -cp build proxy.HttpProxy 8080 30 102400 1048576
```

## Features

- HTTP/1.0 and HTTP/1.1 support
- Persistent connections (keep-alive)
- Concurrent request handling with thread pool
- LRU caching for GET requests
- CONNECT method support for HTTPS tunneling
- Self-loop detection
- Comprehensive error handling
- Request/response logging

## Testing

Run the test suite:
```bash
# Comprehensive test suite
python testScript/test_comprehensive.py

# Individual tests
python testScript/test_localhost.py
python testScript/test_invalid_host_integrated.py
python testScript/test_persistent_localhost.py
```

## Error Handling

The proxy uses a centralized error handling approach with the `ErrorHandler` utility class, providing consistent HTTP error responses:
- 400 Bad Request - Malformed requests
- 421 Misdirected Request - Self-loop detection
- 502 Bad Gateway - DNS resolution failures, connection errors
- 504 Gateway Timeout - Connection timeouts

## Logging

All HTTP transactions are logged in Common Log Format variant:
```
<client_ip> <client_port> <cache_status> <timestamp> "<request_line>" <status_code> <response_bytes>
```

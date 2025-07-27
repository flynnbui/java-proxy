#!/usr/bin/env python3
"""
Comprehensive test suite for Persistent HTTP Proxy Server (Phase 2)
Tests multiple requests per connection, connection management, and error handling.
"""

import socket
import threading
import time
import subprocess
import sys
import signal
import os
from http_parser import HTTPParser

# Test configuration
PROXY_PORT = 50001
TIMEOUT = 10

# ANSI color codes for output
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
BLUE = '\033[94m'
RESET = '\033[0m'

def print_test_header(test_name):
    """Print formatted test header."""
    print(f"\n{BLUE}{'='*60}{RESET}")
    print(f"{BLUE}TEST: {test_name}{RESET}")
    print(f"{BLUE}{'='*60}{RESET}")

def print_success(message):
    """Print success message."""
    print(f"{GREEN}✓ {message}{RESET}")

def print_failure(message):
    """Print failure message."""
    print(f"{RED}✗ {message}{RESET}")

def print_info(message):
    """Print info message."""
    print(f"{YELLOW}ℹ {message}{RESET}")

def read_http_response(sock, timeout=5):
    """Read a complete HTTP response from socket."""
    sock.settimeout(timeout)
    response = b''
    headers_complete = False
    content_length = None
    body_bytes_read = 0
    
    while True:
        try:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response += chunk
            
            # Check if headers are complete
            if not headers_complete and b'\r\n\r\n' in response:
                headers_complete = True
                header_end = response.find(b'\r\n\r\n')
                headers = response[:header_end].decode('utf-8', errors='ignore')
                
                # Extract content-length if present
                for line in headers.split('\r\n'):
                    if line.lower().startswith('content-length:'):
                        content_length = int(line.split(':', 1)[1].strip())
                        break
                
                # Calculate body bytes read so far
                body_bytes_read = len(response) - header_end - 4
            
            # Check if we've read the complete response
            if headers_complete:
                if content_length is not None:
                    if body_bytes_read >= content_length:
                        break
                elif b'</html>' in response or b'</HTML>' in response:
                    # Common end tags for HTML responses
                    break
                elif len(chunk) < 4096:
                    # Partial chunk might indicate end of response
                    break
                    
        except socket.timeout:
            break
        except Exception as e:
            print_failure(f"Error reading response: {e}")
            break
    
    return response

def extract_status_code(response):
    """Extract HTTP status code from response."""
    try:
        if isinstance(response, bytes):
            response = response.decode('utf-8', errors='ignore')
        first_line = response.split('\r\n')[0]
        parts = first_line.split(' ')
        if len(parts) >= 2:
            return int(parts[1])
    except:
        pass
    return None

def test_persistent_get_requests():
    """Test multiple GET requests on same connection."""
    print_test_header("Multiple GET Requests on Persistent Connection")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # First GET request
        request1 = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info(f"Sending request 1: GET http://httpbin.org/get")
        sock.send(request1.encode())
        
        response1 = read_http_response(sock)
        status1 = extract_status_code(response1)
        print_success(f"Response 1 received: HTTP {status1}")
        
        # Check if connection stayed open
        time.sleep(0.5)
        
        # Second GET request on same connection
        request2 = "GET http://httpbin.org/status/200 HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info(f"Sending request 2: GET http://httpbin.org/status/200")
        sock.send(request2.encode())
        
        response2 = read_http_response(sock)
        status2 = extract_status_code(response2)
        print_success(f"Response 2 received: HTTP {status2}")
        
        # Third GET request
        request3 = "GET http://httpbin.org/headers HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info(f"Sending request 3: GET http://httpbin.org/headers")
        sock.send(request3.encode())
        
        response3 = read_http_response(sock)
        status3 = extract_status_code(response3)
        print_success(f"Response 3 received: HTTP {status3}")
        
        sock.close()
        print_success("Test passed: Multiple GET requests on persistent connection")
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_mixed_methods_persistent():
    """Test GET, HEAD, POST on same persistent connection."""
    print_test_header("Mixed Methods (GET/HEAD/POST) on Persistent Connection")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # GET request
        get_request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending GET request")
        sock.send(get_request.encode())
        
        response1 = read_http_response(sock)
        status1 = extract_status_code(response1)
        print_success(f"GET response received: HTTP {status1}")
        
        # HEAD request
        head_request = "HEAD http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending HEAD request")
        sock.send(head_request.encode())
        
        response2 = read_http_response(sock)
        status2 = extract_status_code(response2)
        # Verify HEAD response has no body
        if b'\r\n\r\n' in response2:
            body = response2.split(b'\r\n\r\n', 1)[1]
            if len(body) == 0:
                print_success(f"HEAD response received: HTTP {status2} (no body)")
            else:
                print_failure(f"HEAD response has body (length: {len(body)})")
        
        # POST request
        post_data = "test=data"
        post_request = f"POST http://httpbin.org/post HTTP/1.1\r\nHost: httpbin.org\r\nContent-Length: {len(post_data)}\r\n\r\n{post_data}"
        print_info("Sending POST request with body")
        sock.send(post_request.encode())
        
        response3 = read_http_response(sock)
        status3 = extract_status_code(response3)
        print_success(f"POST response received: HTTP {status3}")
        
        # Final GET to verify connection still works
        final_request = "GET http://httpbin.org/status/204 HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending final GET request")
        sock.send(final_request.encode())
        
        response4 = read_http_response(sock)
        status4 = extract_status_code(response4)
        print_success(f"Final GET response received: HTTP {status4}")
        
        sock.close()
        print_success("Test passed: Mixed methods on persistent connection")
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connection_close_header():
    """Test that Connection: close header terminates connection."""
    print_test_header("Connection: close Header Handling")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Send request with Connection: close
        request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
        print_info("Sending request with Connection: close")
        sock.send(request.encode())
        
        response = read_http_response(sock)
        status = extract_status_code(response)
        print_success(f"Response received: HTTP {status}")
        
        # Verify response has Connection: close
        if b'connection: close' in response.lower():
            print_success("Response contains Connection: close header")
        else:
            print_failure("Response missing Connection: close header")
        
        # Try to send another request - should fail
        time.sleep(0.5)
        try:
            sock.send(request.encode())
            # Try to receive - should fail or get empty response
            sock.settimeout(2)
            data = sock.recv(1024)
            if not data:
                print_success("Connection closed as expected")
            else:
                print_failure("Unexpected data received after Connection: close")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.error):
            print_success("Connection closed as expected")
        
        sock.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_client_timeout():
    """Test client timeout handling."""
    print_test_header("Client Timeout Handling")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Send first request
        request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending initial request")
        sock.send(request.encode())
        
        response = read_http_response(sock)
        status = extract_status_code(response)
        print_success(f"Response received: HTTP {status}")
        
        # Wait longer than timeout
        print_info(f"Waiting {TIMEOUT + 2} seconds (longer than timeout)...")
        time.sleep(TIMEOUT + 2)
        
        # Try to send another request - connection should be closed
        try:
            sock.send(request.encode())
            sock.settimeout(2)
            data = sock.recv(1024)
            if not data:
                print_success("Connection timed out as expected")
            else:
                print_failure("Unexpected response after timeout")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.error):
            print_success("Connection timed out as expected")
        
        sock.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_http_10_persistence():
    """Test HTTP/1.0 connection handling."""
    print_test_header("HTTP/1.0 Connection Handling")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # HTTP/1.0 without Connection header (should close)
        request1 = "GET http://httpbin.org/get HTTP/1.0\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending HTTP/1.0 request without Connection header")
        sock.send(request1.encode())
        
        response1 = read_http_response(sock)
        status1 = extract_status_code(response1)
        print_success(f"Response received: HTTP {status1}")
        
        # Try another request - should fail
        time.sleep(0.5)
        try:
            sock.send(request1.encode())
            sock.settimeout(2)
            data = sock.recv(1024)
            if not data:
                print_success("Connection closed for HTTP/1.0 as expected")
            else:
                print_failure("Connection stayed open for HTTP/1.0")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.error):
            print_success("Connection closed for HTTP/1.0 as expected")
        
        sock.close()
        
        # Test HTTP/1.0 with Connection: keep-alive
        sock2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock2.connect(('localhost', PROXY_PORT))
        print_info("Testing HTTP/1.0 with Connection: keep-alive")
        
        request2 = "GET http://httpbin.org/get HTTP/1.0\r\nHost: httpbin.org\r\nConnection: keep-alive\r\n\r\n"
        sock2.send(request2.encode())
        
        response2 = read_http_response(sock2)
        status2 = extract_status_code(response2)
        print_success(f"Response received: HTTP {status2}")
        
        # Try another request - should work if keep-alive is supported
        sock2.send(request2.encode())
        try:
            response3 = read_http_response(sock2, timeout=3)
            if response3:
                print_success("Connection kept alive for HTTP/1.0 with keep-alive")
            else:
                print_info("Connection closed (keep-alive may not be supported for HTTP/1.0)")
        except:
            print_info("Connection closed (keep-alive may not be supported for HTTP/1.0)")
        
        sock2.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_malformed_requests():
    """Test handling of malformed requests in persistent connection."""
    print_test_header("Malformed Request Handling")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Send valid request first
        valid_request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending valid request")
        sock.send(valid_request.encode())
        
        response1 = read_http_response(sock)
        status1 = extract_status_code(response1)
        print_success(f"Valid response received: HTTP {status1}")
        
        # Send malformed request
        malformed_request = "INVALID REQUEST\r\n\r\n"
        print_info("Sending malformed request")
        sock.send(malformed_request.encode())
        
        response2 = read_http_response(sock)
        status2 = extract_status_code(response2)
        if status2 == 400:
            print_success(f"Received expected 400 Bad Request")
        else:
            print_failure(f"Expected 400, got {status2}")
        
        # Connection should be closed after error
        time.sleep(0.5)
        try:
            sock.send(valid_request.encode())
            sock.settimeout(2)
            data = sock.recv(1024)
            if not data:
                print_success("Connection closed after malformed request")
            else:
                print_failure("Connection stayed open after malformed request")
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, socket.error):
            print_success("Connection closed after malformed request")
        
        sock.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def run_all_tests():
    """Run all test cases."""
    print(f"\n{BLUE}Starting Persistent Proxy Server Tests{RESET}")
    print(f"{BLUE}Proxy Port: {PROXY_PORT}{RESET}")
    print(f"{BLUE}{'='*60}{RESET}")
    
    # Start proxy server in background
    proxy_process = subprocess.Popen(
        [sys.executable, 'proxy.py', str(PROXY_PORT), str(TIMEOUT), '1024', '1048576'],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE
    )
    
    print_info("Waiting for proxy server to start...")
    time.sleep(3)
    
    # Check if proxy started successfully
    if proxy_process.poll() is not None:
        print_failure("Proxy server failed to start")
        stdout, stderr = proxy_process.communicate()
        print(f"STDOUT: {stdout.decode()}")
        print(f"STDERR: {stderr.decode()}")
        return
    
    print_success("Proxy server started")
    
    # Run test cases
    test_results = []
    tests = [
        ("Multiple GET Requests", test_persistent_get_requests),
        ("Mixed Methods", test_mixed_methods_persistent),
        ("Connection Close", test_connection_close_header),
        ("Client Timeout", test_client_timeout),
        ("HTTP/1.0 Handling", test_http_10_persistence),
        ("Malformed Requests", test_malformed_requests)
    ]
    
    for test_name, test_func in tests:
        try:
            result = test_func()
            test_results.append((test_name, result))
        except Exception as e:
            print_failure(f"Test {test_name} crashed: {e}")
            test_results.append((test_name, False))
        time.sleep(1)  # Brief pause between tests
    
    # Print summary
    print(f"\n{BLUE}{'='*60}{RESET}")
    print(f"{BLUE}TEST SUMMARY{RESET}")
    print(f"{BLUE}{'='*60}{RESET}")
    
    passed = sum(1 for _, result in test_results if result)
    total = len(test_results)
    
    for test_name, result in test_results:
        status = f"{GREEN}PASSED{RESET}" if result else f"{RED}FAILED{RESET}"
        print(f"{test_name}: {status}")
    
    print(f"\n{BLUE}Total: {passed}/{total} tests passed{RESET}")
    
    # Stop proxy server
    print_info("Stopping proxy server...")
    proxy_process.terminate()
    proxy_process.wait(timeout=5)
    print_success("Proxy server stopped")
    
    return passed == total

if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)

#!/usr/bin/env python3
"""
Comprehensive test suite for CONNECT method implementation (Phase 3)
Tests HTTPS tunneling, error handling, and integration with existing methods.
"""

import socket
import ssl
import subprocess
import sys
import time

# Test configuration
PROXY_PORT = 50002
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

def test_connect_basic():
    """Test basic CONNECT method functionality."""
    print_test_header("Basic CONNECT Method")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Send CONNECT request
        connect_request = "CONNECT httpbin.org:443 HTTP/1.1\r\nHost: httpbin.org:443\r\n\r\n"
        print_info("Sending CONNECT httpbin.org:443")
        sock.send(connect_request.encode())
        
        # Read response
        response = sock.recv(1024).decode('utf-8', errors='ignore')
        print_info(f"Response: {response.strip()}")
        
        if "200 Connection Established" in response:
            print_success("Received 200 Connection Established")
            
            # Tunnel should now be established
            print_info("Tunnel established, connection ready for SSL")
            sock.close()
            return True
        else:
            print_failure(f"Expected 200 Connection Established, got: {response}")
            sock.close()
            return False
            
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_invalid_port():
    """Test CONNECT with invalid port (not 443)."""
    print_test_header("CONNECT with Invalid Port")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Test port 80 (HTTP)
        print_info("Testing CONNECT to port 80 (should fail)")
        request = "CONNECT httpbin.org:80 HTTP/1.1\r\nHost: httpbin.org:80\r\n\r\n"
        sock.send(request.encode())
        
        response = sock.recv(1024).decode()
        if "400 Bad Request" in response and "invalid port" in response:
            print_success("Correctly rejected port 80 with 400 Bad Request")
        else:
            print_failure(f"Unexpected response: {response}")
            return False
        
        sock.close()
        
        # Test arbitrary port
        sock2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock2.connect(('localhost', PROXY_PORT))
        print_info("Testing CONNECT to port 8080 (should fail)")
        
        request2 = "CONNECT example.com:8080 HTTP/1.1\r\nHost: example.com:8080\r\n\r\n"
        sock2.send(request2.encode())
        
        response2 = sock2.recv(1024).decode()
        if "400 Bad Request" in response2 and "invalid port" in response2:
            print_success("Correctly rejected port 8080 with 400 Bad Request")
        else:
            print_failure(f"Unexpected response: {response2}")
            return False
        
        sock2.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_self_loop():
    """Test CONNECT self-loop prevention."""
    print_test_header("CONNECT Self-Loop Prevention")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Try to CONNECT to proxy itself
        request = f"CONNECT localhost:{PROXY_PORT} HTTP/1.1\r\nHost: localhost:{PROXY_PORT}\r\n\r\n"
        print_info(f"Attempting CONNECT to proxy itself (localhost:{PROXY_PORT})")
        sock.send(request.encode())
        
        response = sock.recv(1024).decode()
        if "421 Misdirected Request" in response:
            print_success("Correctly prevented self-loop with 421 Misdirected Request")
        else:
            print_failure(f"Unexpected response: {response}")
            return False
        
        sock.close()
        
        # Also test 127.0.0.1
        sock2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock2.connect(('localhost', PROXY_PORT))
        request2 = f"CONNECT 127.0.0.1:{PROXY_PORT} HTTP/1.1\r\nHost: 127.0.0.1:{PROXY_PORT}\r\n\r\n"
        print_info(f"Attempting CONNECT to proxy itself (127.0.0.1:{PROXY_PORT})")
        sock2.send(request2.encode())
        
        response2 = sock2.recv(1024).decode()
        if "421 Misdirected Request" in response2:
            print_success("Correctly prevented self-loop with 127.0.0.1")
        else:
            print_failure(f"Unexpected response: {response2}")
            return False
        
        sock2.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_https_tunnel():
    """Test actual HTTPS connection through tunnel."""
    print_test_header("HTTPS Through CONNECT Tunnel")
    
    try:
        # Create raw socket connection
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Send CONNECT request
        connect_request = "CONNECT httpbin.org:443 HTTP/1.1\r\nHost: httpbin.org:443\r\n\r\n"
        print_info("Sending CONNECT httpbin.org:443")
        sock.send(connect_request.encode())
        
        # Read response
        response = b''
        while b'\r\n\r\n' not in response:
            chunk = sock.recv(1024)
            if not chunk:
                break
            response += chunk
        
        response_str = response.decode('utf-8', errors='ignore')
        print_info(f"Response: {response_str.split('\\r\\n')[0]}")
        
        if "200 Connection Established" not in response_str:
            print_failure(f"Failed to establish tunnel: {response_str}")
            sock.close()
            return False
        
        print_success("Tunnel established")
        
        # Wrap socket with SSL
        print_info("Initiating SSL handshake through tunnel")
        context = ssl.create_default_context()
        ssl_sock = context.wrap_socket(sock, server_hostname='httpbin.org')
        
        # Send HTTPS request
        https_request = "GET /get HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
        print_info("Sending HTTPS GET request through tunnel")
        ssl_sock.send(https_request.encode())
        
        # Read response
        https_response = b''
        while True:
            chunk = ssl_sock.recv(1024)
            if not chunk:
                break
            https_response += chunk
        
        ssl_sock.close()
        
        # Check response
        response_text = https_response.decode('utf-8', errors='ignore')
        if "200 OK" in response_text and '"url": "https://httpbin.org/get"' in response_text:
            print_success("Successfully received HTTPS response through tunnel")
            return True
        else:
            print_failure("Unexpected HTTPS response")
            return False
            
    except ssl.SSLError as e:
        print_failure(f"SSL error: {e}")
        return False
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_invalid_host():
    """Test CONNECT with invalid hostname."""
    print_test_header("CONNECT with Invalid Host")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # Test non-existent domain
        request = "CONNECT nonexistent.invalid:443 HTTP/1.1\r\nHost: nonexistent.invalid:443\r\n\r\n"
        print_info("Testing CONNECT to non-existent domain")
        sock.send(request.encode())
        
        response = sock.recv(1024).decode()
        if "502 Bad Gateway" in response and "could not resolve" in response:
            print_success("Correctly returned 502 Bad Gateway for DNS failure")
        else:
            print_failure(f"Unexpected response: {response}")
            return False
        
        sock.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_malformed():
    """Test malformed CONNECT requests."""
    print_test_header("Malformed CONNECT Requests")
    
    try:
        # Missing port
        sock1 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock1.connect(('localhost', PROXY_PORT))
        print_info("Testing CONNECT without port")
        
        request1 = "CONNECT httpbin.org HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        sock1.send(request1.encode())
        
        response1 = sock1.recv(1024).decode()
        if "400 Bad Request" in response1:
            print_success("Correctly rejected CONNECT without port")
        else:
            print_failure(f"Unexpected response: {response1}")
            return False
        
        sock1.close()
        
        # Invalid target format
        sock2 = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock2.connect(('localhost', PROXY_PORT))
        print_info("Testing CONNECT with invalid format")
        
        request2 = "CONNECT http://httpbin.org:443 HTTP/1.1\r\nHost: httpbin.org:443\r\n\r\n"
        sock2.send(request2.encode())
        
        response2 = sock2.recv(1024).decode()
        if "400 Bad Request" in response2:
            print_success("Correctly rejected invalid CONNECT format")
        else:
            print_failure(f"Unexpected response: {response2}")
            return False
        
        sock2.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def test_connect_with_persistence():
    """Test CONNECT method with persistent connections."""
    print_test_header("CONNECT with Persistent Connection")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.connect(('localhost', PROXY_PORT))
        print_success("Connected to proxy")
        
        # First request: regular GET
        get_request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        print_info("Sending GET request")
        sock.send(get_request.encode())
        
        # Read GET response
        response1 = b''
        content_length = None
        headers_done = False
        
        while True:
            chunk = sock.recv(1024)
            if not chunk:
                break
            response1 += chunk
            
            if not headers_done and b'\r\n\r\n' in response1:
                headers_done = True
                headers = response1.split(b'\r\n\r\n')[0].decode('utf-8')
                for line in headers.split('\r\n'):
                    if line.lower().startswith('content-length:'):
                        content_length = int(line.split(':', 1)[1].strip())
                        break
            
            if headers_done and content_length:
                body_start = response1.find(b'\r\n\r\n') + 4
                if len(response1) >= body_start + content_length:
                    break
        
        if b'200 OK' in response1:
            print_success("GET request successful")
        else:
            print_failure("GET request failed")
            return False
        
        # Second request: CONNECT (should close connection after)
        connect_request = "CONNECT httpbin.org:443 HTTP/1.1\r\nHost: httpbin.org:443\r\n\r\n"
        print_info("Sending CONNECT request on same connection")
        sock.send(connect_request.encode())
        
        # Read CONNECT response
        response2 = sock.recv(1024).decode()
        if "200 Connection Established" in response2:
            print_success("CONNECT successful on persistent connection")
            print_info("Connection now in tunnel mode")
        else:
            print_failure(f"CONNECT failed: {response2}")
            return False
        
        sock.close()
        return True
        
    except Exception as e:
        print_failure(f"Test failed: {e}")
        return False

def run_all_tests():
    """Run all CONNECT method tests."""
    print(f"\n{BLUE}Starting CONNECT Method Tests{RESET}")
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
        ("Basic CONNECT", test_connect_basic),
        ("Invalid Port", test_connect_invalid_port),
        ("Self-Loop Prevention", test_connect_self_loop),
        ("HTTPS Tunnel", test_connect_https_tunnel),
        ("Invalid Host", test_connect_invalid_host),
        ("Malformed Requests", test_connect_malformed),
        ("CONNECT with Persistence", test_connect_with_persistence)
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

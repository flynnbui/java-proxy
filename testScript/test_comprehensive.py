#!/usr/bin/env python3
"""
Comprehensive test suite for HTTP proxy after refactoring
"""
import subprocess
import socket
import time
import threading
import sys
import os
from http.server import HTTPServer, BaseHTTPRequestHandler

class TestHTTPServer(BaseHTTPRequestHandler):
    """Simple HTTP server for testing"""
    def do_GET(self):
        if self.path == '/test':
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            self.send_header('Content-Length', '13')
            self.end_headers()
            self.wfile.write(b'Test response')
        elif self.path == '/large':
            self.send_response(200)
            self.send_header('Content-Type', 'text/plain')
            # Don't send Content-Length to test connection close handling
            self.send_header('Connection', 'close')
            self.end_headers()
            self.wfile.write(b'Large response ' * 100)
        else:
            self.send_response(404)
            self.end_headers()
    
    def do_HEAD(self):
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', '13')
        self.end_headers()
    
    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        post_data = self.rfile.read(content_length)
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(post_data)))
        self.end_headers()
        self.wfile.write(post_data)
    
    def log_message(self, format, *args):
        pass  # Suppress logging

def start_test_server(port):
    """Start a test HTTP server in background"""
    server = HTTPServer(('localhost', port), TestHTTPServer)
    thread = threading.Thread(target=server.serve_forever)
    thread.daemon = True
    thread.start()
    time.sleep(0.5)
    return server

def test_proxy_with_fresh_instance():
    """Run comprehensive tests with a fresh proxy instance"""
    
    # Start test HTTP server
    test_server_port = 8890
    test_server = start_test_server(test_server_port)
    
    # Start proxy
    print("Starting proxy for comprehensive testing...")
    proxy_port = 50800
    proxy = subprocess.Popen(
        ["java", "-cp", "out", "proxy.HttpProxy", str(proxy_port), "10", "1024", "1048576"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )
    
    # Give proxy time to start
    time.sleep(2)
    
    tests_passed = 0
    tests_failed = 0
    
    # Test 1: Basic GET request
    print("\n1. Testing basic GET request...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = f"GET http://localhost:{test_server_port}/test HTTP/1.1\r\n"
        request += f"Host: localhost:{test_server_port}\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        sock.send(request.encode())
        response = sock.recv(4096).decode()
        sock.close()
        
        if "200 OK" in response and "Test response" in response:
            print("✓ Basic GET request passed")
            tests_passed += 1
        else:
            print("✗ Basic GET request failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ Basic GET request failed: {e}")
        tests_failed += 1
    
    # Test 2: HEAD request
    print("\n2. Testing HEAD request...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = f"HEAD http://localhost:{test_server_port}/test HTTP/1.1\r\n"
        request += f"Host: localhost:{test_server_port}\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        sock.send(request.encode())
        response = sock.recv(4096).decode()
        sock.close()
        
        if "200 OK" in response and "Content-Length: 13" in response and "Test response" not in response:
            print("✓ HEAD request passed")
            tests_passed += 1
        else:
            print("✗ HEAD request failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ HEAD request failed: {e}")
        tests_failed += 1
    
    # Test 3: POST request
    print("\n3. Testing POST request...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        post_data = "test=data"
        request = f"POST http://localhost:{test_server_port}/test HTTP/1.1\r\n"
        request += f"Host: localhost:{test_server_port}\r\n"
        request += f"Content-Length: {len(post_data)}\r\n"
        request += "Content-Type: application/x-www-form-urlencoded\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        request += post_data
        
        sock.send(request.encode())
        response = sock.recv(4096).decode()
        sock.close()
        
        if "200 OK" in response and post_data in response:
            print("✓ POST request passed")
            tests_passed += 1
        else:
            print("✗ POST request failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ POST request failed: {e}")
        tests_failed += 1
    
    # Test 4: Invalid host handling
    print("\n4. Testing invalid host handling...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = "GET http://nonexistent-host-12345.invalid/test HTTP/1.1\r\n"
        request += "Host: nonexistent-host-12345.invalid\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        sock.send(request.encode())
        response = sock.recv(4096).decode()
        sock.close()
        
        if "502 Bad Gateway" in response or "504 Gateway Timeout" in response:
            print("✓ Invalid host handling passed")
            tests_passed += 1
        else:
            print("✗ Invalid host handling failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ Invalid host handling failed: {e}")
        tests_failed += 1
    
    # Test 5: Self-loop detection
    print("\n5. Testing self-loop detection...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = f"GET http://localhost:{proxy_port}/test HTTP/1.1\r\n"
        request += f"Host: localhost:{proxy_port}\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        sock.send(request.encode())
        response = sock.recv(4096).decode()
        sock.close()
        
        if "421 Misdirected Request" in response and "Self-loop detected" in response:
            print("✓ Self-loop detection passed")
            tests_passed += 1
        else:
            print("✗ Self-loop detection failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ Self-loop detection failed: {e}")
        tests_failed += 1
    
    # Test 6: Response without Content-Length
    print("\n6. Testing response without Content-Length...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = f"GET http://localhost:{test_server_port}/large HTTP/1.1\r\n"
        request += f"Host: localhost:{test_server_port}\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        sock.send(request.encode())
        response = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response += chunk
        sock.close()
        
        response_str = response.decode()
        if "200 OK" in response_str and "Large response" in response_str:
            print("✓ Response without Content-Length passed")
            tests_passed += 1
        else:
            print("✗ Response without Content-Length failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ Response without Content-Length failed: {e}")
        tests_failed += 1
    
    # Test 7: Persistent connections
    print("\n7. Testing persistent connections...")
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('127.0.0.1', proxy_port))
        
        # First request with keep-alive
        request1 = f"GET http://localhost:{test_server_port}/test HTTP/1.1\r\n"
        request1 += f"Host: localhost:{test_server_port}\r\n"
        request1 += "Connection: keep-alive\r\n"
        request1 += "\r\n"
        
        sock.send(request1.encode())
        response1 = b""
        while b"\r\n\r\n" not in response1:
            response1 += sock.recv(1024)
        
        # Read body based on Content-Length
        headers_end = response1.find(b"\r\n\r\n") + 4
        headers = response1[:headers_end].decode()
        body_so_far = response1[headers_end:]
        
        content_length = 13  # We know this from the test server
        while len(body_so_far) < content_length:
            body_so_far += sock.recv(1024)
        
        # Second request on same connection
        request2 = f"GET http://localhost:{test_server_port}/test HTTP/1.1\r\n"
        request2 += f"Host: localhost:{test_server_port}\r\n"
        request2 += "Connection: close\r\n"
        request2 += "\r\n"
        
        sock.send(request2.encode())
        response2 = sock.recv(4096)
        sock.close()
        
        if b"200 OK" in response1 and b"200 OK" in response2:
            print("✓ Persistent connections passed")
            tests_passed += 1
        else:
            print("✗ Persistent connections failed")
            tests_failed += 1
    except Exception as e:
        print(f"✗ Persistent connections failed: {e}")
        tests_failed += 1
    
    # Terminate proxy
    print("\nTerminating proxy...")
    proxy.terminate()
    proxy.wait()
    
    # Shutdown test server
    test_server.shutdown()
    
    # Print summary
    print(f"\n{'='*50}")
    print(f"Comprehensive Test Summary:")
    print(f"Total tests: {tests_passed + tests_failed}")
    print(f"Passed: {tests_passed}")
    print(f"Failed: {tests_failed}")
    print(f"{'='*50}")
    
    return tests_failed == 0

if __name__ == "__main__":
    success = test_proxy_with_fresh_instance()
    sys.exit(0 if success else 1)

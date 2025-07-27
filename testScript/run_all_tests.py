#!/usr/bin/env python3
"""
Comprehensive test runner for Java HTTP Proxy.
Runs multiple test scenarios to verify proxy functionality.
"""

import subprocess
import socket
import time
import sys
import os
import threading
import json

class ProxyTester:
    def __init__(self):
        self.build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
        self.results = []
        
    def find_available_port(self):
        """Find an available port for testing."""
        for port in range(50100, 60000):
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.bind(('localhost', port))
                sock.close()
                return port
            except:
                continue
        return None
    
    def start_proxy(self, port):
        """Start the Java proxy server."""
        process = subprocess.Popen(
            ["java", "-cp", self.build_dir, "com.comp3331.proxy.HttpProxy", 
             str(port), "10", "1048576", "10485760"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        time.sleep(2)  # Give proxy time to start
        return process
    
    def test_basic_connection(self, proxy_port):
        """Test basic proxy connection."""
        print("\n1. Testing basic connection...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5)
            sock.connect(('localhost', proxy_port))
            sock.close()
            print("  ✓ Can connect to proxy")
            return True
        except Exception as e:
            print(f"  ✗ Failed to connect: {e}")
            return False
    
    def test_http_get(self, proxy_port):
        """Test HTTP GET through proxy."""
        print("\n2. Testing HTTP GET request...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', proxy_port))
            
            # Send GET request
            request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
            sock.send(request.encode())
            
            # Receive response
            response = b""
            while True:
                data = sock.recv(4096)
                if not data:
                    break
                response += data
            
            sock.close()
            
            response_str = response.decode('utf-8', errors='ignore')
            if "200 OK" in response_str:
                print("  ✓ GET request successful")
                return True
            else:
                print(f"  ✗ GET request failed: {response_str[:200]}")
                return False
                
        except Exception as e:
            print(f"  ✗ GET request error: {e}")
            return False
    
    def test_http_post(self, proxy_port):
        """Test HTTP POST through proxy."""
        print("\n3. Testing HTTP POST request...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', proxy_port))
            
            # Send POST request
            body = "test=data&foo=bar"
            request = f"POST http://httpbin.org/post HTTP/1.1\r\n"
            request += "Host: httpbin.org\r\n"
            request += "Content-Type: application/x-www-form-urlencoded\r\n"
            request += f"Content-Length: {len(body)}\r\n"
            request += "Connection: close\r\n\r\n"
            request += body
            
            sock.send(request.encode())
            
            # Receive response
            response = b""
            while True:
                data = sock.recv(4096)
                if not data:
                    break
                response += data
            
            sock.close()
            
            response_str = response.decode('utf-8', errors='ignore')
            if "200 OK" in response_str and "test" in response_str:
                print("  ✓ POST request successful")
                return True
            else:
                print(f"  ✗ POST request failed")
                return False
                
        except Exception as e:
            print(f"  ✗ POST request error: {e}")
            return False
    
    def test_http_head(self, proxy_port):
        """Test HTTP HEAD through proxy."""
        print("\n4. Testing HTTP HEAD request...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', proxy_port))
            
            # Send HEAD request
            request = "HEAD http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
            sock.send(request.encode())
            
            # Receive response
            response = b""
            while True:
                data = sock.recv(4096)
                if not data:
                    break
                response += data
            
            sock.close()
            
            response_str = response.decode('utf-8', errors='ignore')
            if "200 OK" in response_str and len(response_str) < 1000:  # HEAD should have no body
                print("  ✓ HEAD request successful (no body)")
                return True
            else:
                print(f"  ✗ HEAD request failed")
                return False
                
        except Exception as e:
            print(f"  ✗ HEAD request error: {e}")
            return False
    
    def test_invalid_host(self, proxy_port):
        """Test proxy behavior with invalid host."""
        print("\n5. Testing invalid host handling...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', proxy_port))
            
            # Send request to invalid host
            request = "GET http://this-host-does-not-exist-12345.invalid/test HTTP/1.1\r\n"
            request += "Host: this-host-does-not-exist-12345.invalid\r\n"
            request += "Connection: close\r\n\r\n"
            sock.send(request.encode())
            
            # Receive response
            response = sock.recv(4096).decode('utf-8', errors='ignore')
            sock.close()
            
            if "502" in response or "504" in response or "500" in response:
                print("  ✓ Invalid host properly handled with error")
                return True
            else:
                print(f"  ✗ Unexpected response for invalid host")
                return False
                
        except Exception as e:
            print(f"  ✗ Invalid host test error: {e}")
            return False
    
    def test_self_loop(self, proxy_port):
        """Test self-loop detection."""
        print("\n6. Testing self-loop detection...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5)
            sock.connect(('localhost', proxy_port))
            
            # Send request to proxy itself
            request = f"GET http://localhost:{proxy_port}/test HTTP/1.1\r\n"
            request += f"Host: localhost:{proxy_port}\r\n"
            request += "Connection: close\r\n\r\n"
            sock.send(request.encode())
            
            # Receive response
            response = sock.recv(4096).decode('utf-8', errors='ignore')
            sock.close()
            
            if "421" in response or "403" in response or "400" in response:
                print("  ✓ Self-loop properly detected and blocked")
                return True
            else:
                print(f"  ✗ Self-loop not detected properly")
                return False
                
        except Exception as e:
            print(f"  ✗ Self-loop test error: {e}")
            return False
    
    def test_persistent_connection(self, proxy_port):
        """Test persistent connection handling."""
        print("\n7. Testing persistent connections...")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', proxy_port))
            
            # Send first request
            request1 = "GET http://httpbin.org/get?request=1 HTTP/1.1\r\n"
            request1 += "Host: httpbin.org\r\n"
            request1 += "Connection: keep-alive\r\n\r\n"
            sock.send(request1.encode())
            
            # Read first response
            response1 = b""
            content_length = None
            headers_done = False
            body_read = 0
            
            while True:
                data = sock.recv(1)
                if not data:
                    break
                response1 += data
                
                if not headers_done and b"\r\n\r\n" in response1:
                    headers_done = True
                    headers = response1.split(b"\r\n\r\n")[0].decode('utf-8')
                    for line in headers.split("\r\n"):
                        if line.lower().startswith("content-length:"):
                            content_length = int(line.split(":")[1].strip())
                            break
                
                if headers_done and content_length:
                    body_start = response1.find(b"\r\n\r\n") + 4
                    body_read = len(response1) - body_start
                    if body_read >= content_length:
                        break
            
            # Send second request on same connection
            request2 = "GET http://httpbin.org/get?request=2 HTTP/1.1\r\n"
            request2 += "Host: httpbin.org\r\n"
            request2 += "Connection: close\r\n\r\n"
            sock.send(request2.encode())
            
            # Read second response
            response2 = sock.recv(4096)
            sock.close()
            
            response1_str = response1.decode('utf-8', errors='ignore')
            response2_str = response2.decode('utf-8', errors='ignore')
            
            if "200 OK" in response1_str and "200 OK" in response2_str:
                print("  ✓ Persistent connections working")
                return True
            else:
                print(f"  ✗ Persistent connections failed")
                return False
                
        except Exception as e:
            print(f"  ✗ Persistent connection test error: {e}")
            return False
    
    def run_all_tests(self):
        """Run all tests."""
        print("=" * 60)
        print("Java HTTP Proxy Comprehensive Test Suite")
        print("=" * 60)
        
        # Find available port
        proxy_port = self.find_available_port()
        if not proxy_port:
            print("✗ Could not find available port for testing")
            return False
        
        print(f"\nStarting proxy on port {proxy_port}...")
        proxy_process = self.start_proxy(proxy_port)
        
        # Check if proxy started
        if proxy_process.poll() is not None:
            _, stderr = proxy_process.communicate()
            print(f"✗ Proxy failed to start: {stderr}")
            return False
        
        print("✓ Proxy started successfully")
        
        # Run tests
        tests = [
            ("Basic Connection", self.test_basic_connection),
            ("HTTP GET", self.test_http_get),
            ("HTTP POST", self.test_http_post),
            ("HTTP HEAD", self.test_http_head),
            ("Invalid Host", self.test_invalid_host),
            ("Self-Loop Detection", self.test_self_loop),
            ("Persistent Connections", self.test_persistent_connection)
        ]
        
        passed = 0
        failed = 0
        
        for test_name, test_func in tests:
            try:
                if test_func(proxy_port):
                    passed += 1
                    self.results.append((test_name, True, None))
                else:
                    failed += 1
                    self.results.append((test_name, False, "Test failed"))
            except Exception as e:
                failed += 1
                self.results.append((test_name, False, str(e)))
        
        # Cleanup
        print("\nStopping proxy...")
        proxy_process.terminate()
        try:
            proxy_process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            proxy_process.kill()
        
        # Summary
        print("\n" + "=" * 60)
        print("TEST SUMMARY")
        print("=" * 60)
        for test_name, success, error in self.results:
            status = "✓ PASS" if success else "✗ FAIL"
            print(f"{status}: {test_name}")
            if error:
                print(f"       Error: {error}")
        
        print(f"\nTotal: {passed} passed, {failed} failed")
        print("=" * 60)
        
        return failed == 0


def main():
    tester = ProxyTester()
    success = tester.run_all_tests()
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())

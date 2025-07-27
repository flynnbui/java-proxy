#!/usr/bin/env python3
"""
Test persistent connections with localhost HTTP server.
"""

import socket
import subprocess
import time
import os
import sys
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

class TestHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        # Handle different paths
        if self.path == '/test1':
            content = b'Response 1'
        elif self.path == '/test2':
            content = b'Response 2'
        else:
            content = b'Default response'
            
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)
    
    def log_message(self, format, *args):
        pass  # Suppress logging

def run_test_server(port):
    server = HTTPServer(('localhost', port), TestHandler)
    server.serve_forever()

def test_persistent_connection():
    # Start test HTTP server
    server_port = 8889
    server_thread = threading.Thread(target=run_test_server, args=(server_port,))
    server_thread.daemon = True
    server_thread.start()
    time.sleep(1)  # Let server start
    
    # Start proxy
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    proxy = subprocess.Popen(
        ["java", "-cp", build_dir, "proxy.HttpProxy", "50400", "30", "1048576", "10485760"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    time.sleep(2)  # Let proxy start
    
    try:
        # Test persistent connection with multiple requests
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        sock.connect(('localhost', 50400))
        
        # First request with keep-alive
        request1 = f"GET http://localhost:{server_port}/test1 HTTP/1.1\r\n"
        request1 += f"Host: localhost:{server_port}\r\n"
        request1 += "Connection: keep-alive\r\n\r\n"
        
        print(f"Sending first request:\n{request1}")
        sock.send(request1.encode())
        
        # Read first response
        response1 = b""
        content_length = 0
        headers_done = False
        
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
            
            if headers_done and content_length > 0:
                body_start = response1.find(b"\r\n\r\n") + 4
                body_read = len(response1) - body_start
                if body_read >= content_length:
                    break
        
        response1_str = response1.decode('utf-8', errors='ignore')
        print(f"\nFirst response:\n{response1_str}")
        
        if "200 OK" not in response1_str or "Response 1" not in response1_str:
            print("✗ First request failed")
            return False
        
        # Second request on same connection
        request2 = f"GET http://localhost:{server_port}/test2 HTTP/1.1\r\n"
        request2 += f"Host: localhost:{server_port}\r\n"
        request2 += "Connection: close\r\n\r\n"
        
        print(f"\nSending second request:\n{request2}")
        sock.send(request2.encode())
        
        # Read second response
        response2 = b""
        while True:
            data = sock.recv(1024)
            if not data:
                break
            response2 += data
        
        sock.close()
        
        response2_str = response2.decode('utf-8', errors='ignore')
        print(f"\nSecond response:\n{response2_str}")
        
        if "200 OK" in response2_str and "Response 2" in response2_str:
            print("\n✓ Persistent connection test successful!")
            return True
        else:
            print("\n✗ Second request failed")
            return False
            
    except Exception as e:
        print(f"Error: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        proxy.terminate()
        proxy.wait()

if __name__ == "__main__":
    success = test_persistent_connection()
    sys.exit(0 if success else 1)

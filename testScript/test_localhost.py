#!/usr/bin/env python3
"""
Test proxy with localhost HTTP server to avoid external network issues.
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
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', '13')
        self.end_headers()
        self.wfile.write(b'Hello, World!')
    
    def log_message(self, format, *args):
        pass  # Suppress logging

def run_test_server(port):
    server = HTTPServer(('localhost', port), TestHandler)
    server.serve_forever()

def test_localhost_get():
    # Start test HTTP server
    server_port = 8888
    server_thread = threading.Thread(target=run_test_server, args=(server_port,))
    server_thread.daemon = True
    server_thread.start()
    time.sleep(1)  # Let server start
    
    # Start proxy
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    proxy = subprocess.Popen(
        ["java", "-cp", build_dir, "com.comp3331.proxy.HttpProxy", "50300", "10", "1048576", "10485760"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    time.sleep(2)  # Let proxy start
    
    try:
        # Test GET request through proxy
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect(('localhost', 50300))
        
        request = f"GET http://localhost:{server_port}/ HTTP/1.1\r\nHost: localhost:{server_port}\r\nConnection: close\r\n\r\n"
        print(f"Sending request:\n{request}")
        sock.send(request.encode())
        
        # Read response
        response = b""
        while True:
            data = sock.recv(1024)
            if not data:
                break
            response += data
        
        sock.close()
        
        response_str = response.decode('utf-8', errors='ignore')
        print(f"\nResponse:\n{response_str}")
        
        if "200 OK" in response_str and "Hello, World!" in response_str:
            print("\n✓ Localhost GET request successful!")
            return True
        else:
            print("\n✗ Localhost GET request failed")
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
    success = test_localhost_get()
    sys.exit(0 if success else 1)

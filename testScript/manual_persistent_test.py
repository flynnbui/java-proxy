#!/usr/bin/env python3
"""
Manual test demonstrating persistent connection behavior.
Shows connection reuse across multiple requests.
"""

import socket
import time

def manual_test_persistent():
    """Demonstrate persistent connection with detailed output."""
    proxy_host = 'localhost'
    proxy_port = 50000
    
    print("=== Manual Persistent Connection Test ===\n")
    
    # Create connection
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((proxy_host, proxy_port))
    local_port = sock.getsockname()[1]
    print(f"Connected to proxy from local port {local_port}\n")
    
    # Request 1: GET
    print("REQUEST 1: GET http://httpbin.org/get")
    request1 = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
    sock.send(request1.encode())
    
    # Read response
    response1 = b''
    sock.settimeout(5)
    while True:
        try:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response1 += chunk
            if b'\r\n\r\n' in response1:
                # Check content-length
                headers = response1.split(b'\r\n\r\n')[0].decode('utf-8')
                body_start = response1.find(b'\r\n\r\n') + 4
                for line in headers.split('\r\n'):
                    if line.lower().startswith('content-length:'):
                        content_len = int(line.split(':', 1)[1].strip())
                        if len(response1) >= body_start + content_len:
                            break
        except socket.timeout:
            break
    
    print(f"Response 1 received: {len(response1)} bytes")
    print(f"Connection still open on local port {local_port}\n")
    
    time.sleep(1)
    
    # Request 2: HEAD
    print("REQUEST 2: HEAD http://httpbin.org/get")
    request2 = "HEAD http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
    sock.send(request2.encode())
    
    # Read response (HEAD has no body)
    response2 = b''
    while True:
        try:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response2 += chunk
            if b'\r\n\r\n' in response2:
                break
        except socket.timeout:
            break
    
    print(f"Response 2 received: {len(response2)} bytes (HEAD - no body)")
    print(f"Connection still open on local port {local_port}\n")
    
    time.sleep(1)
    
    # Request 3: POST
    print("REQUEST 3: POST http://httpbin.org/post")
    post_data = "test=persistent&connection=reuse"
    request3 = f"POST http://httpbin.org/post HTTP/1.1\r\nHost: httpbin.org\r\nContent-Length: {len(post_data)}\r\n\r\n{post_data}"
    sock.send(request3.encode())
    
    # Read response
    response3 = b''
    while True:
        try:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response3 += chunk
            if b'\r\n\r\n' in response3:
                headers = response3.split(b'\r\n\r\n')[0].decode('utf-8')
                body_start = response3.find(b'\r\n\r\n') + 4
                for line in headers.split('\r\n'):
                    if line.lower().startswith('content-length:'):
                        content_len = int(line.split(':', 1)[1].strip())
                        if len(response3) >= body_start + content_len:
                            break
        except socket.timeout:
            break
    
    print(f"Response 3 received: {len(response3)} bytes")
    print(f"Connection still open on local port {local_port}\n")
    
    # Final request with Connection: close
    print("REQUEST 4: GET with Connection: close")
    request4 = "GET http://httpbin.org/status/200 HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
    sock.send(request4.encode())
    
    # Read response
    response4 = b''
    while True:
        try:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response4 += chunk
        except socket.timeout:
            break
    
    print(f"Response 4 received: {len(response4)} bytes")
    print("Connection should close after this response\n")
    
    # Verify connection is closed
    time.sleep(1)
    try:
        sock.send(b"test")
        print("ERROR: Connection still open!")
    except:
        print("✓ Connection closed as expected")
    
    sock.close()
    print("\n=== Test Complete ===")
    print(f"Successfully sent 4 requests over a single connection (port {local_port})")

if __name__ == "__main__":
    try:
        manual_test_persistent()
    except Exception as e:
        print(f"Test failed: {e}")

#!/usr/bin/env python3
"""
Simple GET test to diagnose timeout issues.
"""

import socket
import subprocess
import time
import os
import sys

def test_simple_get():
    # Start proxy
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    proxy = subprocess.Popen(
        ["java", "-cp", build_dir, "proxy.HttpProxy", "50200", "30", "1048576", "10485760"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    time.sleep(2)  # Let proxy start
    
    try:
        # Test with a simple, fast-responding server
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(30)  # Long timeout
        sock.connect(('localhost', 50200))
        
        # Request a small, simple page
        request = "GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n"
        print(f"Sending request:\n{request}")
        sock.send(request.encode())
        
        # Read response with timeout handling
        response = b""
        sock.settimeout(5)  # Shorter timeout for reading
        
        try:
            while True:
                data = sock.recv(1024)
                if not data:
                    break
                response += data
                print(f"Received {len(data)} bytes")
        except socket.timeout:
            print("Socket read timeout - got partial response")
        
        sock.close()
        
        response_str = response.decode('utf-8', errors='ignore')
        print(f"\nResponse preview:\n{response_str[:500]}")
        
        if "200 OK" in response_str:
            print("\n✓ GET request successful!")
            return True
        else:
            print("\n✗ GET request failed")
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
    success = test_simple_get()
    sys.exit(0 if success else 1)

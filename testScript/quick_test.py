#!/usr/bin/env python3
"""
Quick test to verify Java proxy can start and accept connections.
"""

import subprocess
import socket
import time
import sys
import os

def test_basic_proxy():
    """Test if proxy starts and accepts connections."""
    print("Quick Java Proxy Test")
    print("=" * 40)
    
    # Get build directory
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    print(f"Build directory: {build_dir}")
    
    # Test port
    test_port = 50001
    
    print(f"\nStarting proxy on port {test_port}...")
    
    # Start proxy
    proxy_process = subprocess.Popen(
        ["java", "-cp", build_dir, "com.comp3331.proxy.HttpProxy", str(test_port), "10", "1024", "1048576"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    # Give it time to start
    time.sleep(2)
    
    # Check if process is still running
    if proxy_process.poll() is not None:
        stdout, stderr = proxy_process.communicate()
        print(f"Proxy failed to start!")
        print(f"STDOUT: {stdout}")
        print(f"STDERR: {stderr}")
        return False
    
    print("Proxy started successfully!")
    print("\nTrying to connect...")
    
    try:
        # Try to connect
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.settimeout(3)
        client_socket.connect(('localhost', test_port))
        print("Connected to proxy!")
        
        # Send a simple request
        request = "GET http://example.com/ HTTP/1.1\r\nHost: example.com\r\n\r\n"
        print(f"\nSending request:\n{request.strip()}")
        client_socket.send(request.encode())
        
        # Try to receive response
        response = client_socket.recv(4096).decode('utf-8', errors='ignore')
        print(f"\nReceived response:\n{response[:500]}...")
        
        client_socket.close()
        
    except Exception as e:
        print(f"Error during connection test: {e}")
    
    # Clean up
    print("\nStopping proxy...")
    proxy_process.terminate()
    try:
        proxy_process.wait(timeout=2)
    except subprocess.TimeoutExpired:
        proxy_process.kill()
    
    print("Test complete!")

if __name__ == "__main__":
    test_basic_proxy()

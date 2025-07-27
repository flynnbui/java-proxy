#!/usr/bin/env python3
"""
Test invalid host error handling.
"""

import socket
import subprocess
import time
import os
import sys

def test_invalid_host():
    # Start proxy
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    proxy = subprocess.Popen(
        ["java", "-cp", build_dir, "proxy.HttpProxy", "50500", "5", "1048576", "10485760"],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True
    )
    
    time.sleep(2)  # Let proxy start
    
    test_cases = [
        ("nonexistent-host-12345.invalid", "Invalid hostname"),
        ("10.0.0.0", "Unreachable IP"),
        ("this-should-not-exist.test", "Non-existent domain")
    ]
    
    results = []
    
    for host, description in test_cases:
        print(f"\nTesting {description}: {host}")
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('localhost', 50500))
            
            request = f"GET http://{host}/test HTTP/1.1\r\nHost: {host}\r\nConnection: close\r\n\r\n"
            print(f"Request: {request.strip()}")
            sock.send(request.encode())
            
            # Read response
            response = b""
            start_time = time.time()
            while True:
                try:
                    data = sock.recv(1024)
                    if not data:
                        break
                    response += data
                except socket.timeout:
                    print("Socket timeout after", time.time() - start_time, "seconds")
                    break
            
            sock.close()
            
            response_str = response.decode('utf-8', errors='ignore')
            print(f"Response length: {len(response_str)}")
            
            if response_str:
                print(f"Response: {response_str[:200]}...")
                if "502" in response_str or "504" in response_str:
                    print("✓ Got error response")
                    results.append((host, True))
                else:
                    print("✗ Unexpected response")
                    results.append((host, False))
            else:
                print("✗ No response received")
                results.append((host, False))
                
        except Exception as e:
            print(f"✗ Error: {e}")
            results.append((host, False))
    
    proxy.terminate()
    proxy.wait()
    
    # Summary
    print("\n" + "="*50)
    print("Test Summary:")
    passed = sum(1 for _, result in results if result)
    print(f"Passed: {passed}/{len(results)}")
    
    return passed == len(results)

if __name__ == "__main__":
    success = test_invalid_host()
    sys.exit(0 if success else 1)

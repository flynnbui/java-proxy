#!/usr/bin/env python3
"""
Integrated test for invalid host handling with fresh proxy startup
"""
import subprocess
import socket
import time
import threading
import sys

def test_invalid_hosts_with_fresh_proxy():
    """Test invalid host handling with a fresh proxy instance"""
    
    # Start proxy
    print("Starting fresh proxy instance...")
    proxy_port = 50700
    proxy = subprocess.Popen(
        ["java", "-cp", "out", "com.comp3331.proxy.HttpProxy", str(proxy_port), "10", "1024", "1048576"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )
    
    # Give proxy time to start
    time.sleep(2)
    
    invalid_hosts = [
        ("Invalid hostname", "nonexistent-host-12345.invalid"),
        ("Unreachable IP", "10.0.0.0"),
        ("Non-existent domain", "this-should-not-exist.test"),
    ]
    
    passed = 0
    failed = 0
    
    for test_name, host in invalid_hosts:
        print(f"\nTesting {test_name}: {host}")
        
        try:
            # Connect to proxy
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(10)
            sock.connect(('127.0.0.1', proxy_port))
            
            # Send request
            request = f"GET http://{host}/test HTTP/1.1\r\n"
            request += f"Host: {host}\r\n"
            request += "Connection: close\r\n"
            request += "\r\n"
            
            print(f"Request: {request.strip()}")
            sock.send(request.encode())
            
            # Receive response
            response = b""
            start_time = time.time()
            while time.time() - start_time < 10:
                try:
                    chunk = sock.recv(4096)
                    if not chunk:
                        break
                    response += chunk
                    # Check if we have a complete response
                    if b"\r\n\r\n" in response:
                        # Check Content-Length
                        headers_end = response.find(b"\r\n\r\n") + 4
                        headers = response[:headers_end].decode()
                        if "Content-Length:" in headers:
                            content_length = None
                            for line in headers.split("\r\n"):
                                if line.startswith("Content-Length:"):
                                    content_length = int(line.split(":")[1].strip())
                                    break
                            if content_length is not None:
                                body_length = len(response) - headers_end
                                if body_length >= content_length:
                                    break
                except socket.timeout:
                    continue
            
            sock.close()
            
            print(f"Response length: {len(response)}")
            if response:
                print(f"Response: {response.decode()}")
                
                # Check for expected error response
                response_str = response.decode()
                if ("502 Bad Gateway" in response_str or 
                    "504 Gateway Timeout" in response_str or
                    "503 Service Unavailable" in response_str):
                    print("✓ Got error response")
                    passed += 1
                else:
                    print("✗ Unexpected response")
                    failed += 1
            else:
                print("✗ No response received")
                failed += 1
                
        except Exception as e:
            print(f"✗ Error: {e}")
            failed += 1
        
        # Small delay between tests
        time.sleep(0.5)
    
    # Terminate proxy
    print("\nTerminating proxy...")
    proxy.terminate()
    proxy.wait()
    
    print(f"\n{'='*50}")
    print(f"Test Summary:")
    print(f"Passed: {passed}/{len(invalid_hosts)}")
    
    return passed == len(invalid_hosts)

if __name__ == "__main__":
    success = test_invalid_hosts_with_fresh_proxy()
    sys.exit(0 if success else 1)

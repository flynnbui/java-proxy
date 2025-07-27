#!/usr/bin/env python3
"""
Test script for invalid host handling with debugging output
"""
import socket
import time

def test_invalid_hosts_debug():
    proxy_host = '127.0.0.1'
    proxy_port = 50500
    
    invalid_hosts = [
        ("Invalid hostname", "nonexistent-host-12345.invalid"),
        ("Unreachable IP", "10.0.0.0"),
        ("Non-existent domain", "this-should-not-exist.test"),
    ]
    
    for test_name, host in invalid_hosts:
        print(f"\nTesting {test_name}: {host}")
        
        try:
            # Connect to proxy
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.settimeout(5)  # 5 second timeout
            sock.connect((proxy_host, proxy_port))
            
            # Send request
            request = f"GET http://{host}/test HTTP/1.1\r\n"
            request += f"Host: {host}\r\n"
            request += "Connection: close\r\n"
            request += "\r\n"
            
            print(f"Request: {request.strip()}")
            sock.send(request.encode())
            
            # Try to receive response
            response = b""
            start_time = time.time()
            sock.settimeout(0.1)  # Small timeout for non-blocking reads
            
            while time.time() - start_time < 5:  # Wait up to 5 seconds
                try:
                    chunk = sock.recv(4096)
                    if not chunk:
                        break
                    response += chunk
                except socket.timeout:
                    # No data available yet, continue waiting
                    continue
                except Exception as e:
                    print(f"Socket error: {e}")
                    break
            
            elapsed = time.time() - start_time
            print(f"Response received after {elapsed:.2f} seconds")
            print(f"Response length: {len(response)}")
            
            if response:
                print(f"Response: {response.decode()}")
                print("✓ Got error response")
            else:
                print("✗ No response received")
                
            sock.close()
            
        except socket.timeout:
            print(f"Socket timeout - no response from proxy")
        except Exception as e:
            print(f"Error: {e}")
        
        # Wait a bit before next test
        time.sleep(1)

if __name__ == "__main__":
    test_invalid_hosts_debug()

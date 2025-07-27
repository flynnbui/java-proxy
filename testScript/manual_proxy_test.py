#!/usr/bin/env python3
"""
Manual proxy test for debugging invalid host handling
"""
import subprocess
import time
import threading
import socket

def start_proxy():
    """Start the proxy and capture output"""
    print("Starting proxy...")
    proxy = subprocess.Popen(
        ["java", "-cp", "out", "com.comp3331.proxy.HttpProxy", "50600", "10", "1024", "1048576"],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1
    )
    
    # Thread to print proxy output
    def print_output():
        while True:
            line = proxy.stdout.readline()
            if not line:
                break
            print(f"[PROXY] {line.strip()}")
    
    output_thread = threading.Thread(target=print_output)
    output_thread.daemon = True
    output_thread.start()
    
    time.sleep(2)  # Give proxy time to start
    return proxy

def test_invalid_host(proxy_port):
    """Test invalid host handling"""
    print("\nTesting invalid host...")
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(10)
        sock.connect(('127.0.0.1', proxy_port))
        
        request = "GET http://nonexistent-host-12345.invalid/test HTTP/1.1\r\n"
        request += "Host: nonexistent-host-12345.invalid\r\n"
        request += "Connection: close\r\n"
        request += "\r\n"
        
        print(f"Sending request:\n{request}")
        sock.send(request.encode())
        
        print("Waiting for response...")
        response = b""
        start_time = time.time()
        
        while time.time() - start_time < 10:
            try:
                chunk = sock.recv(4096)
                if not chunk:
                    break
                response += chunk
            except socket.timeout:
                continue
        
        if response:
            print(f"\nReceived response ({len(response)} bytes):")
            print(response.decode())
        else:
            print("\nNo response received")
        
        sock.close()
    except Exception as e:
        print(f"Error: {e}")

def main():
    proxy = start_proxy()
    
    try:
        test_invalid_host(50600)
        
        print("\nWaiting 5 seconds before shutdown...")
        time.sleep(5)
        
    finally:
        print("\nTerminating proxy...")
        proxy.terminate()
        proxy.wait()

if __name__ == "__main__":
    main()

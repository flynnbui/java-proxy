#!/usr/bin/env python3
"""
Basic testing script for Phase 1.1 HTTP Proxy Setup.
Tests argument validation and basic socket functionality.
Modified to test Java implementation.
"""

import subprocess
import socket
import time
import sys
import os


def test_argument_validation():
    """Test command line argument validation."""
    print("Testing argument validation...")
    
    # Test cases: (args, should_succeed, description)
    test_cases = [
        ([], False, "No arguments"),
        (["50000"], False, "Too few arguments"),
        (["50000", "10", "1024"], False, "Missing one argument"),
        (["50000", "10", "1024", "1048576", "extra"], False, "Too many arguments"),
        (["invalid", "10", "1024", "1048576"], False, "Invalid port"),
        (["50000", "invalid", "1024", "1048576"], False, "Invalid timeout"),
        (["50000", "10", "invalid", "1048576"], False, "Invalid max_object_size"),
        (["50000", "10", "1024", "invalid"], False, "Invalid max_cache_size"),
        (["100", "10", "1024", "1048576"], False, "Port too low"),
        (["70000", "10", "1024", "1048576"], False, "Port too high"),
        (["50000", "0", "1024", "1048576"], False, "Zero timeout"),
        (["50000", "-1", "1024", "1048576"], False, "Negative timeout"),
        (["50000", "10", "0", "1048576"], False, "Zero max_object_size"),
        (["50000", "10", "1024", "0"], False, "Zero max_cache_size"),
        (["50000", "10", "2048", "1024"], False, "max_cache_size < max_object_size"),
        (["50000", "10", "1024", "1048576"], True, "Valid arguments"),
        (["60893", "10", "1024", "1048576"], True, "Valid arguments (example from spec)"),
    ]
    
    passed = 0
    failed = 0
    
    # Get the absolute path to the build directory
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    
    for args, should_succeed, description in test_cases:
        try:
            # Run Java proxy with test arguments
            result = subprocess.run(
                ["java", "-cp", build_dir, "proxy.HttpProxy"] + args,
                capture_output=True,
                text=True,
                timeout=2
            )
            
            # Check if it succeeded as expected
            if should_succeed:
                if result.returncode == 0 or "Starting HTTP proxy" in result.stdout:
                    print(f"  ✓ {description}")
                    passed += 1
                else:
                    print(f"  ✗ {description} - Expected success but got error: {result.stderr}")
                    failed += 1
            else:
                if result.returncode != 0:
                    print(f"  ✓ {description}")
                    passed += 1
                else:
                    print(f"  ✗ {description} - Expected failure but succeeded")
                    failed += 1
                    
        except subprocess.TimeoutExpired:
            # Timeout means the proxy started successfully (good for valid args)
            if should_succeed:
                print(f"  ✓ {description}")
                passed += 1
            else:
                print(f"  ✗ {description} - Expected failure but proxy started")
                failed += 1
        except KeyboardInterrupt:
            print("\nTest interrupted by user")
            return False
        except Exception as e:
            print(f"  ✗ {description} - Test error: {e}")
            failed += 1
    
    print(f"\nArgument validation tests: {passed} passed, {failed} failed")
    return failed == 0


def test_socket_binding():
    """Test basic socket binding functionality."""
    print("\nTesting socket binding...")
    
    # Find an available port
    test_port = find_available_port()
    if not test_port:
        print("  ✗ Could not find available port for testing")
        return False
    
    print(f"  Using port {test_port} for testing")
    
    # Get the absolute path to the build directory
    build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
    
    # Start proxy in background
    proxy_process = None
    try:
        proxy_process = subprocess.Popen(
            ["java", "-cp", build_dir, "proxy.HttpProxy", str(test_port), "5", "1024", "1048576"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        # Give it time to start
        time.sleep(1)
        
        # Check if process is still running
        if proxy_process.poll() is not None:
            _, stderr = proxy_process.communicate()
            print(f"  ✗ Proxy failed to start: {stderr}")
            return False
        
        # Try to connect to the proxy
        try:
            client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            client_socket.settimeout(3)
            client_socket.connect(('localhost', test_port))
            
            # Send a simple HTTP request
            request = "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
            client_socket.send(request.encode())
            
            # Receive response
            response = client_socket.recv(1024).decode()
            client_socket.close()
            
            # Check if we got any HTTP response (the Java proxy might have different response content)
            if "HTTP/1.1" in response or "HTTP/1.0" in response:
                print("  ✓ Socket binding and basic response working")
                print(f"  Response preview: {response[:100].strip()}...")
                return True
            else:
                print(f"  ✗ Unexpected response: {response[:100]}...")
                return False
                
        except Exception as e:
            print(f"  ✗ Failed to connect to proxy: {e}")
            return False
            
    finally:
        if proxy_process:
            proxy_process.terminate()
            try:
                proxy_process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proxy_process.kill()


def find_available_port():
    """Find an available port for testing."""
    for port in range(50000, 60000):
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind(('localhost', port))
            sock.close()
            return port
        except:
            continue
    return None


def main():
    """Run all Phase 1.1 tests."""
    print("=== Phase 1.1 HTTP Proxy Setup Tests ===\n")
    
    all_passed = True
    
    # Test argument validation
    if not test_argument_validation():
        all_passed = False
    
    # Test socket binding
    if not test_socket_binding():
        all_passed = False
    
    print(f"\n=== Test Results ===")
    if all_passed:
        print("✓ All Phase 1.1 tests passed!")
        print("Ready to proceed to Phase 1.2: HTTP Message Parsing")
    else:
        print("✗ Some tests failed. Please fix issues before proceeding.")
    
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
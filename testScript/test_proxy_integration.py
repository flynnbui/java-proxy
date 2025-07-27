#!/usr/bin/env python3
"""
Integration test script for HTTP proxy (Phase 1.3).
Tests GET, HEAD, and POST methods with real HTTP requests.
Modified to test Java implementation.
"""

import subprocess
import time
import socket
import sys
import os
import signal

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


def test_proxy_methods():
    """Test HTTP proxy with GET, HEAD, and POST methods."""
    print("=== Phase 1.3 HTTP Proxy Integration Tests ===\n")
    
    # Find available port
    proxy_port = find_available_port()
    if not proxy_port:
        print("✗ Could not find available port for testing")
        return False
    
    print(f"Using proxy port: {proxy_port}")
    
    # Start proxy server
    proxy_process = None
    try:
        print("Starting Java proxy server...")
        build_dir = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "build", "classes")
        proxy_process = subprocess.Popen(
            ["java", "-cp", build_dir, "com.comp3331.proxy.HttpProxy", str(proxy_port), "10", "1024", "1048576"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        # Give proxy time to start
        time.sleep(2)
        
        # Check if proxy started successfully
        if proxy_process.poll() is not None:
            stdout, stderr = proxy_process.communicate()
            print(f"✗ Proxy failed to start: {stderr}")
            return False
        
        print("✓ Proxy server started successfully\n")
        
        # Test cases
        test_results = []
        
        # Test 1: GET request
        print("Test 1: GET request to httpbin.org")
        result = run_curl_test(proxy_port, "GET", "http://httpbin.org/get", [])
        test_results.append(("GET request", result))
        
        # Test 2: HEAD request  
        print("\nTest 2: HEAD request to httpbin.org")
        result = run_curl_test(proxy_port, "HEAD", "http://httpbin.org/get", ["-I"])
        test_results.append(("HEAD request", result))
        
        # Test 3: POST request
        print("\nTest 3: POST request with data")
        result = run_curl_test(proxy_port, "POST", "http://httpbin.org/post", 
                              ["-d", "test=data", "-H", "Content-Type: application/x-www-form-urlencoded"])
        test_results.append(("POST request", result))
        
        # Test 4: Error handling - invalid host
        print("\nTest 4: Error handling - invalid host")
        result = run_curl_test(proxy_port, "GET", "http://nonexistent.invalid/", [], expect_error=True)
        test_results.append(("Invalid host error", result))
        
        # Test 5: Self-loop detection
        print("\nTest 5: Self-loop detection")
        result = run_curl_test(proxy_port, "GET", f"http://localhost:{proxy_port}/", [], expect_error=True)
        test_results.append(("Self-loop detection", result))
        
        # Summary
        print("\n" + "="*50)
        print("TEST RESULTS SUMMARY:")
        print("="*50)
        
        passed = 0
        failed = 0
        
        for test_name, success in test_results:
            status = "✓ PASS" if success else "✗ FAIL"
            print(f"{status}: {test_name}")
            if success:
                passed += 1
            else:
                failed += 1
        
        print(f"\nTotal: {passed} passed, {failed} failed")
        
        # Check proxy logs
        print("\n" + "="*50)
        print("PROXY SERVER OUTPUT:")
        print("="*50)
        
        # Get proxy output (non-blocking)
        try:
            stdout, _ = proxy_process.communicate(timeout=1)
            if stdout:
                print(stdout)
        except subprocess.TimeoutExpired:
            # Proxy still running, kill it gracefully
            proxy_process.terminate()
            try:
                stdout, stderr = proxy_process.communicate(timeout=2)
                if stdout:
                    print(stdout)
                if stderr:
                    print(f"Errors: {stderr}")
            except subprocess.TimeoutExpired:
                proxy_process.kill()
        
        return failed == 0
        
    except Exception as e:
        print(f"✗ Test error: {e}")
        return False
    finally:
        if proxy_process and proxy_process.poll() is None:
            proxy_process.terminate()
            try:
                proxy_process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                proxy_process.kill()


def run_curl_test(proxy_port, method, url, extra_args=[], expect_error=False):
    """Run a curl test through the proxy."""
    try:
        cmd = [
            "curl", 
            "--proxy", f"localhost:{proxy_port}",
            "--max-time", "10",
            "--silent",
            "--write-out", "HTTP_CODE:%{http_code}\\n"
        ]
        
        cmd.extend(extra_args)
        cmd.append(url)
        
        print(f"  Command: {' '.join(cmd)}")
        
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=15
        )
        
        output_lines = result.stdout.strip().split('\n')
        http_code = None
        
        # Extract HTTP code from output
        for line in output_lines:
            if line.startswith('HTTP_CODE:'):
                http_code = line.split(':', 1)[1]
                break
        
        print(f"  HTTP Code: {http_code}")
        print(f"  Return Code: {result.returncode}")
        
        if result.stderr:
            print(f"  Errors: {result.stderr}")
        
        # Check success criteria
        if expect_error:
            # For error tests, we expect either 4xx/5xx status or curl error
            success = (http_code and (http_code.startswith('4') or http_code.startswith('5'))) or result.returncode != 0
            print(f"  Result: {'✓ Expected error occurred' if success else '✗ Expected error but got success'}")
        else:
            # For success tests, we expect 2xx status and curl success
            success = http_code and http_code.startswith('2') and result.returncode == 0
            print(f"  Result: {'✓ Success' if success else '✗ Failed'}")
        
        return success
        
    except subprocess.TimeoutExpired:
        print("  ✗ Request timed out")
        return False
    except Exception as e:
        print(f"  ✗ Test error: {e}")
        return False


def main():
    """Run all integration tests."""
    if not test_proxy_methods():
        print("\n✗ Some tests failed!")
        return 1
    else:
        print("\n✓ All tests passed! Phase 1.3 proxy implementation is working correctly.")
        return 0


if __name__ == "__main__":
    sys.exit(main())
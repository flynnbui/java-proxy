#!/usr/bin/env python3
"""
Test suite for HTTP parsing functionality (Phase 1.2).
Tests request parsing, response parsing, and edge cases.
"""

import sys
from http_parser import HTTPParser, HTTPRequest, HTTPResponse, HTTPParseError
from http_stream import HTTPMessageBuilder


def test_request_parsing():
    """Test HTTP request parsing functionality."""
    print("Testing HTTP request parsing...")
    
    passed = 0
    failed = 0
    
    # Test basic GET request
    try:
        request_data = "GET /index.html HTTP/1.1\r\nHost: example.com\r\nUser-Agent: test\r\n\r\n"
        request = HTTPParser.parse_request(request_data)
        
        assert request.method == "GET"
        assert request.target == "/index.html"
        assert request.version == "HTTP/1.1"
        assert request.get_header("host") == "example.com"
        assert request.get_header("user-agent") == "test"
        assert not request.has_body()
        
        print("  ✓ Basic GET request parsing")
        passed += 1
    except Exception as e:
        print(f"  ✗ Basic GET request parsing: {e}")
        failed += 1
    
    # Test POST request with body
    try:
        request_data = "POST /submit HTTP/1.1\r\nHost: example.com\r\nContent-Length: 13\r\n\r\nHello, World!"
        request = HTTPParser.parse_request(request_data)
        
        assert request.method == "POST"
        assert request.target == "/submit"
        assert request.has_body()
        assert request.get_content_length() == 13
        assert request.body == b"Hello, World!"
        
        print("  ✓ POST request with body")
        passed += 1
    except Exception as e:
        print(f"  ✗ POST request with body: {e}")
        failed += 1
    
    # Test case-insensitive headers
    try:
        request_data = "GET / HTTP/1.1\r\nHOST: example.com\r\nUser-Agent: test\r\n\r\n"
        request = HTTPParser.parse_request(request_data)
        
        assert request.get_header("host") == "example.com"
        assert request.get_header("HOST") == "example.com"
        assert request.get_header("User-Agent") == "test"
        
        print("  ✓ Case-insensitive header access")
        passed += 1
    except Exception as e:
        print(f"  ✗ Case-insensitive header access: {e}")
        failed += 1
    
    # Test absolute-form URL (proxy request)
    try:
        request_data = "GET http://example.com/path HTTP/1.1\r\nHost: example.com\r\n\r\n"
        request = HTTPParser.parse_request(request_data)
        
        assert request.target == "http://example.com/path"
        
        print("  ✓ Absolute-form URL parsing")
        passed += 1
    except Exception as e:
        print(f"  ✗ Absolute-form URL parsing: {e}")
        failed += 1
    
    # Test CONNECT request (authority-form)
    try:
        request_data = "CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\n\r\n"
        request = HTTPParser.parse_request(request_data)
        
        assert request.method == "CONNECT"
        assert request.target == "example.com:443"
        assert not request.has_body()
        
        print("  ✓ CONNECT request parsing")
        passed += 1
    except Exception as e:
        print(f"  ✗ CONNECT request parsing: {e}")
        failed += 1
    
    # Test invalid request line
    try:
        request_data = "INVALID REQUEST\r\n\r\n"
        HTTPParser.parse_request(request_data)
        print("  ✗ Invalid request line - should have failed")
        failed += 1
    except HTTPParseError:
        print("  ✓ Invalid request line properly rejected")
        passed += 1
    except Exception as e:
        print(f"  ✗ Invalid request line - wrong exception: {e}")
        failed += 1
    
    print(f"Request parsing tests: {passed} passed, {failed} failed")
    return failed == 0


def test_response_parsing():
    """Test HTTP response parsing functionality."""
    print("\nTesting HTTP response parsing...")
    
    passed = 0
    failed = 0
    
    # Test basic 200 OK response
    try:
        response_data = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 13\r\n\r\nHello, World!"
        response = HTTPParser.parse_response(response_data)
        
        assert response.version == "HTTP/1.1"
        assert response.status_code == 200
        assert response.reason_phrase == "OK"
        assert response.get_header("content-type") == "text/html"
        assert response.get_content_length() == 13
        assert response.body == b"Hello, World!"
        
        print("  ✓ Basic 200 OK response")
        passed += 1
    except Exception as e:
        print(f"  ✗ Basic 200 OK response: {e}")
        failed += 1
    
    # Test 404 response
    try:
        response_data = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: 9\r\n\r\nNot Found"
        response = HTTPParser.parse_response(response_data)
        
        assert response.status_code == 404
        assert response.reason_phrase == "Not Found"
        assert response.body == b"Not Found"
        
        print("  ✓ 404 Not Found response")
        passed += 1
    except Exception as e:
        print(f"  ✗ 404 Not Found response: {e}")
        failed += 1
    
    # Test response without body (HEAD request simulation)
    try:
        response_data = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 1000\r\n\r\n"
        response = HTTPParser.parse_response(response_data)
        
        # For HEAD request, response has no body even with Content-Length
        assert not response.has_body(request_method='HEAD')
        assert response.has_body(request_method='GET')  # Would have body for GET
        
        print("  ✓ HEAD response body detection")
        passed += 1
    except Exception as e:
        print(f"  ✗ HEAD response body detection: {e}")
        failed += 1
    
    # Test 204 No Content (never has body)
    try:
        response_data = "HTTP/1.1 204 No Content\r\n\r\n"
        response = HTTPParser.parse_response(response_data)
        
        assert response.status_code == 204
        assert not response.has_body(request_method='GET')
        
        print("  ✓ 204 No Content response")
        passed += 1
    except Exception as e:
        print(f"  ✗ 204 No Content response: {e}")
        failed += 1
    
    # Test invalid status code
    try:
        response_data = "HTTP/1.1 999 Invalid\r\n\r\n"
        HTTPParser.parse_response(response_data)
        print("  ✗ Invalid status code - should have failed")
        failed += 1
    except HTTPParseError:
        print("  ✓ Invalid status code properly rejected")
        passed += 1
    except Exception as e:
        print(f"  ✗ Invalid status code - wrong exception: {e}")
        failed += 1
    
    print(f"Response parsing tests: {passed} passed, {failed} failed")
    return failed == 0


def test_message_building():
    """Test HTTP message building functionality."""
    print("\nTesting HTTP message building...")
    
    passed = 0
    failed = 0
    
    # Test request building
    try:
        headers = {"Host": "example.com", "User-Agent": "test"}
        request_data = HTTPMessageBuilder.build_request("GET", "/test", headers=headers)
        
        # Parse it back to verify
        request = HTTPParser.parse_request(request_data.decode('utf-8'))
        assert request.method == "GET"
        assert request.target == "/test"
        assert request.get_header("host") == "example.com"
        
        print("  ✓ Request building")
        passed += 1
    except Exception as e:
        print(f"  ✗ Request building: {e}")
        failed += 1
    
    # Test response building
    try:
        headers = {"Content-Type": "text/plain", "Content-Length": "5"}
        response_data = HTTPMessageBuilder.build_response(200, headers=headers, body="Hello")
        
        # Parse it back to verify
        response = HTTPParser.parse_response(response_data.decode('utf-8'))
        assert response.status_code == 200
        assert response.reason_phrase == "OK"
        assert response.body == b"Hello"
        
        print("  ✓ Response building")
        passed += 1
    except Exception as e:
        print(f"  ✗ Response building: {e}")
        failed += 1
    
    print(f"Message building tests: {passed} passed, {failed} failed")
    return failed == 0


def test_edge_cases():
    """Test edge cases and error conditions."""
    print("\nTesting edge cases...")
    
    passed = 0
    failed = 0
    
    # Test empty data
    try:
        HTTPParser.parse_request("")
        print("  ✗ Empty request - should have failed")
        failed += 1
    except HTTPParseError:
        print("  ✓ Empty request properly rejected")
        passed += 1
    except Exception as e:
        print(f"  ✗ Empty request - wrong exception: {e}")
        failed += 1
    
    # Test malformed header
    try:
        request_data = "GET / HTTP/1.1\r\nBadHeader\r\n\r\n"
        HTTPParser.parse_request(request_data)
        print("  ✗ Malformed header - should have failed")
        failed += 1
    except HTTPParseError:
        print("  ✓ Malformed header properly rejected")
        passed += 1
    except Exception as e:
        print(f"  ✗ Malformed header - wrong exception: {e}")
        failed += 1
    
    # Test binary data handling
    try:
        request_data = b"GET / HTTP/1.1\r\nHost: example.com\r\n\r\n"
        request = HTTPParser.parse_request(request_data)
        assert request.method == "GET"
        
        print("  ✓ Binary data handling")
        passed += 1
    except Exception as e:
        print(f"  ✗ Binary data handling: {e}")
        failed += 1
    
    print(f"Edge case tests: {passed} passed, {failed} failed")
    return failed == 0


def main():
    """Run all HTTP parsing tests."""
    print("=== Phase 1.2 HTTP Parsing Tests ===\n")
    
    all_passed = True
    
    if not test_request_parsing():
        all_passed = False
    
    if not test_response_parsing():
        all_passed = False
    
    if not test_message_building():
        all_passed = False
    
    if not test_edge_cases():
        all_passed = False
    
    print(f"\n=== Test Results ===")
    if all_passed:
        print("✓ All Phase 1.2 HTTP parsing tests passed!")
        print("Ready to proceed to Phase 1.3: Basic Non-Persistent Proxy Implementation")
    else:
        print("✗ Some tests failed. Please fix issues before proceeding.")
    
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
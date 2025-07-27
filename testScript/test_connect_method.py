"""
Test suite for CONNECT method in ConnectProxyServer.
"""

import pytest
import socket
import threading
import time
from connect_proxy_server import ConnectProxyServer
from config import ProxyConfig

# Configure proxy server parameters
PROXY_PORT = 50000
TIMEOUT = 10
MAX_OBJECT_SIZE = 1024
MAX_CACHE_SIZE = 1048576

# Helper functions for test setup

def start_proxy_server():
    config = ProxyConfig(port=PROXY_PORT, timeout=TIMEOUT, max_object_size=MAX_OBJECT_SIZE, max_cache_size=MAX_CACHE_SIZE)
    proxy = ConnectProxyServer(config)
    thread = threading.Thread(target=proxy.run)
    thread.daemon = True
    thread.start()
    time.sleep(1)  # Allow some time for the server to start
    return proxy


# Fixtures

@pytest.fixture(scope='module')
def proxy_server():
    proxy = start_proxy_server()
    yield proxy
    proxy.running = False


def test_connect_https(proxy_server):
    """Test CONNECT method for HTTPS tunneling."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        connect_request = "CONNECT httpbin.org:443 HTTP/1.1\r\nHost: httpbin.org:443\r\n\r\n"
        sock.send(connect_request.encode())
        
        response = sock.recv(1024).decode()
        assert "200 Connection Established" in response


def test_connect_invalid_port(proxy_server):
    """Test CONNECT with invalid port."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        connect_request = "CONNECT httpbin.org:80 HTTP/1.1\r\nHost: httpbin.org:80\r\n\r\n"
        sock.send(connect_request.encode())
        
        response = sock.recv(1024).decode()
        assert "400 Bad Request" in response
        assert "invalid port" in response


def test_connect_self_loop(proxy_server):
    """Test CONNECT self-loop detection."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        connect_request = f"CONNECT localhost:{PROXY_PORT} HTTP/1.1\r\nHost: localhost:{PROXY_PORT}\r\n\r\n"
        sock.send(connect_request.encode())
        
        response = sock.recv(1024).decode()
        assert "421 Misdirected Request" in response


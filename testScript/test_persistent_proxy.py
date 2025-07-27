# Test suite for the Persistent Proxy Server

import pytest
import socket
import threading
import time
from persistent_proxy_server import PersistentProxyServer
from config import ProxyConfig

# Configure proxy server parameters
PROXY_PORT = 50000
TIMEOUT = 10
MAX_OBJECT_SIZE = 1024
MAX_CACHE_SIZE = 1048576

# Helper functions for test setup

def start_proxy_server():
    config = ProxyConfig(port=PROXY_PORT, timeout=TIMEOUT, max_object_size=MAX_OBJECT_SIZE, max_cache_size=MAX_CACHE_SIZE)
    proxy = PersistentProxyServer(config)
    thread = threading.Thread(target=proxy.run)
    thread.daemon = True
    thread.start()
    time.sleep(1)  # Allow some time for the server to start
    return proxy


# Automated test cases

@pytest.fixture(scope='module')
def proxy_server():
    proxy = start_proxy_server()
    yield proxy
    proxy.running = False


def test_persistent_get_requests(proxy_server):
    """Test multiple GET requests on the same connection."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        sock.sendall(request.encode())
        response1 = sock.recv(4096)
        time.sleep(1)  # Pause to simulate time between requests
        sock.sendall(request.encode())
        response2 = sock.recv(4096)
        assert response1
        assert response2


def test_mixed_methods_persistent(proxy_server):
    """Test GET, HEAD, POST on the same persistent connection."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        get_request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        head_request = "HEAD http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\n\r\n"
        post_request = "POST http://httpbin.org/post HTTP/1.1\r\nHost: httpbin.org\r\nContent-Length: 9\r\n\r\ntest=data"

        sock.sendall(get_request.encode())
        response1 = sock.recv(4096)

        sock.sendall(head_request.encode())
        response2 = sock.recv(4096)

        sock.sendall(post_request.encode())
        response3 = sock.recv(4096)

        assert response1
        assert response2
        assert response3


def test_connection_close(proxy_server):
    """Test that Connection: close header terminates the connection."""
    with socket.create_connection(('localhost', PROXY_PORT)) as sock:
        close_request = "GET http://httpbin.org/get HTTP/1.1\r\nHost: httpbin.org\r\nConnection: close\r\n\r\n"
        sock.sendall(close_request.encode())
        response = sock.recv(4096)
        time.sleep(1)  # Ensure connection closes
        assert response
        with pytest.raises(ConnectionResetError):
            sock.sendall(close_request.encode())  # Should fail as connection is closed


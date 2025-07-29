package proxy;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import proxy.cache.HTTPCacheTest;
import proxy.config.ProxyConfigTest;
import proxy.http.*;
import proxy.logging.ProxyLoggerTest;
import proxy.server.ProxyServerTest;
import proxy.server.ConcurrentProxyServerTest;
import proxy.utils.*;

/**
 * Test suite for the HTTP Proxy implementation.
 * Runs all unit tests to achieve >90% coverage.
 */
@RunWith(Suite.class)
@SuiteClasses({
    // Core HTTP classes
    HTTPRequestTest.class,
    HTTPResponseTest.class,
    HTTPParserTest.class,
    HTTPStreamReaderTest.class,
    HTTPMessageBuilderTest.class,
    
    // Configuration
    ProxyConfigTest.class,
    
    // Cache functionality
    HTTPCacheTest.class,
    
    // Server functionality
    ProxyServerTest.class,
    ConcurrentProxyServerTest.class,
    
    // Utilities
    URLParserTest.class,
    ErrorHandlerTest.class,
    ErrorResponseGeneratorTest.class,
    MessageTransformerTest.class,
    OriginConnectorTest.class,
    
    // Logging
    ProxyLoggerTest.class,
    
    // Integration tests
    ProxyIntegrationTest.class,
    ProxyStressTest.class
})
public class ProxyTestSuite {
    // Test suite class - no additional code needed
}
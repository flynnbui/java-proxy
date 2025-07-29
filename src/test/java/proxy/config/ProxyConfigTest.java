package proxy.config;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for ProxyConfig class.
 */
public class ProxyConfigTest {
    
    @Test
    public void testValidConfiguration() throws ConfigException {
        String[] args = {"8080", "30", "1024", "1048576"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        assertEquals(8080, config.getPort());
        assertEquals(30, config.getTimeout());
        assertEquals(1024, config.getMaxObjectSize());
        assertEquals(1048576, config.getMaxCacheSize());
    }
    
    @Test
    public void testRecommendedPortRange() throws ConfigException {
        // Test port within recommended range (no warning expected)
        String[] args = {"50000", "10", "512", "1024"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        assertEquals(50000, config.getPort());
    }
    
    @Test
    public void testPortBelowRecommended() throws ConfigException {
        // Test port below recommended range (warning expected)
        String[] args = {"8080", "10", "512", "1024"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        assertEquals(8080, config.getPort());
    }
    
    @Test(expected = ConfigException.class)
    public void testInsufficientArguments() throws ConfigException {
        String[] args = {"8080", "30", "1024"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testTooManyArguments() throws ConfigException {
        String[] args = {"8080", "30", "1024", "1048576", "extra"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testInvalidPortFormat() throws ConfigException {
        String[] args = {"not-a-port", "30", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testInvalidTimeoutFormat() throws ConfigException {
        String[] args = {"8080", "not-a-timeout", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testInvalidObjectSizeFormat() throws ConfigException {
        String[] args = {"8080", "30", "not-a-size", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testInvalidCacheSizeFormat() throws ConfigException {
        String[] args = {"8080", "30", "1024", "not-a-size"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testPortTooLow() throws ConfigException {
        String[] args = {"1023", "30", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testPortTooHigh() throws ConfigException {
        String[] args = {"65536", "30", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testNegativePort() throws ConfigException {
        String[] args = {"-1", "30", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testZeroTimeout() throws ConfigException {
        String[] args = {"8080", "0", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testNegativeTimeout() throws ConfigException {
        String[] args = {"8080", "-5", "1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testZeroObjectSize() throws ConfigException {
        String[] args = {"8080", "30", "0", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testNegativeObjectSize() throws ConfigException {
        String[] args = {"8080", "30", "-1024", "1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testZeroCacheSize() throws ConfigException {
        String[] args = {"8080", "30", "1024", "0"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testNegativeCacheSize() throws ConfigException {
        String[] args = {"8080", "30", "1024", "-1048576"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test(expected = ConfigException.class)
    public void testCacheSizeSmallerThanObjectSize() throws ConfigException {
        String[] args = {"8080", "30", "2048", "1024"};
        ProxyConfig.fromArgs(args);
    }
    
    @Test
    public void testCacheSizeEqualToObjectSize() throws ConfigException {
        String[] args = {"8080", "30", "1024", "1024"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        assertEquals(1024, config.getMaxObjectSize());
        assertEquals(1024, config.getMaxCacheSize());
    }
    
    @Test
    public void testMinimumValidPort() throws ConfigException {
        String[] args = {"1024", "30", "512", "1024"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        assertEquals(1024, config.getPort());
    }
    
    @Test
    public void testMaximumValidPort() throws ConfigException {
        String[] args = {"65535", "30", "512", "1024"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        assertEquals(65535, config.getPort());
    }
    
    @Test
    public void testLargeValues() throws ConfigException {
        String[] args = {"50000", "3600", "104857600", "1073741824"}; // 100MB object, 1GB cache
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        assertEquals(50000, config.getPort());
        assertEquals(3600, config.getTimeout()); // 1 hour
        assertEquals(104857600, config.getMaxObjectSize()); // 100MB
        assertEquals(1073741824, config.getMaxCacheSize()); // 1GB
    }
    
    @Test
    public void testSmallValidValues() throws ConfigException {
        String[] args = {"49152", "1", "1", "1"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        assertEquals(49152, config.getPort());
        assertEquals(1, config.getTimeout());
        assertEquals(1, config.getMaxObjectSize());
        assertEquals(1, config.getMaxCacheSize());
    }
    
    @Test
    public void testToString() throws ConfigException {
        String[] args = {"8080", "30", "1024", "1048576"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        String str = config.toString();
        assertTrue("toString should contain port", str.contains("8080"));
        assertTrue("toString should contain timeout", str.contains("30"));
        assertTrue("toString should contain object size", str.contains("1024"));
        assertTrue("toString should contain cache size", str.contains("1048576"));
    }
    
    @Test
    public void testDirectConstruction() {
        ProxyConfig config = new ProxyConfig(8080, 30, 1024, 1048576);
        
        assertEquals(8080, config.getPort());
        assertEquals(30, config.getTimeout());
        assertEquals(1024, config.getMaxObjectSize());
        assertEquals(1048576, config.getMaxCacheSize());
    }
    
    @Test
    public void testConfigurationImmutability() throws ConfigException {
        String[] args = {"8080", "30", "1024", "1048576"};
        ProxyConfig config = ProxyConfig.fromArgs(args);
        
        // Modify the original args array
        args[0] = "9999";
        args[1] = "60";
        
        // Config should remain unchanged
        assertEquals(8080, config.getPort());
        assertEquals(30, config.getTimeout());
    }
    
    @Test
    public void testNumberFormatEdgeCases() {
        // Test various number format edge cases
        String[] testCases = {
            "8080.0", // Float
            "8080L",  // Long suffix
            " 8080 ", // Whitespace
            "+8080",  // Plus sign
            "0x1f90", // Hex
            "010000", // Octal-looking
        };
        
        for (String testCase : testCases) {
            try {
                String[] args = {testCase, "30", "1024", "1048576"};
                ProxyConfig.fromArgs(args);
                fail("Should have thrown ConfigException for: " + testCase);
            } catch (ConfigException e) {
                // Expected - NumberFormatException should be caught and wrapped
                assertTrue("Error message should mention invalid format", 
                          e.getMessage().contains("Invalid argument format"));
            }
        }
    }
}
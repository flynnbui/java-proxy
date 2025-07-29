package proxy.cache;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import proxy.http.HTTPRequest;
import proxy.http.HTTPResponse;

/**
 * Unit tests for HTTPCache class.
 */
public class HTTPCacheTest {
    private HTTPCache cache;
    private HTTPRequest request;
    private HTTPResponse response;
    
    @Before
    public void setUp() {
        cache = new HTTPCache(1024, 512); // 1024 bytes cache, 512 bytes max object
        request = new HTTPRequest();
        response = new HTTPResponse();
    }
    
    @Test
    public void testUrlNormalization() {
        // Test scheme case insensitivity
        assertEquals("http://example.com/", cache.normalizeUrl("HTTP://EXAMPLE.COM/"));
        assertEquals("http://example.com/", cache.normalizeUrl("http://example.com/"));
        
        // Test default port removal
        assertEquals("http://example.com/", cache.normalizeUrl("http://example.com:80/"));
        assertEquals("https://example.com/", cache.normalizeUrl("https://example.com:443/"));
        
        // Test path normalization
        assertEquals("http://example.com/", cache.normalizeUrl("http://example.com"));
        assertEquals("http://example.com/", cache.normalizeUrl("http://example.com/"));
        
        // Test non-default ports preserved
        assertEquals("http://example.com:8080/", cache.normalizeUrl("http://example.com:8080/"));
        
        // Test query string preservation (case sensitive)
        assertEquals("http://example.com/?foo=BAR", cache.normalizeUrl("http://example.com/?foo=BAR"));
        assertNotEquals("http://example.com/?foo=bar", cache.normalizeUrl("http://example.com/?foo=BAR"));
    }
    
    @Test
    public void testUrlNormalizationEdgeCases() {
        // Test malformed URLs
        String malformed = "not-a-url";
        assertEquals(malformed, cache.normalizeUrl(malformed));
        
        // Test complex paths
        assertEquals("http://example.com/path/to/resource", 
                    cache.normalizeUrl("http://example.com/path/to/resource"));
    }
    
    @Test
    public void testCacheabilityRules() {
        request.setMethod("GET");
        response.setStatusCode(200);
        response.setBody("Hello World".getBytes());
        
        // Should be cacheable
        assertTrue(cache.isCacheable(request, response));
        
        // Test non-GET methods
        request.setMethod("POST");
        assertFalse(cache.isCacheable(request, response));
        
        request.setMethod("HEAD");
        assertFalse(cache.isCacheable(request, response));
        
        request.setMethod("CONNECT");
        assertFalse(cache.isCacheable(request, response));
        
        // Test non-200 status codes
        request.setMethod("GET");
        response.setStatusCode(404);
        assertFalse(cache.isCacheable(request, response));
        
        response.setStatusCode(500);
        assertFalse(cache.isCacheable(request, response));
        
        response.setStatusCode(304);
        assertFalse(cache.isCacheable(request, response));
        
        // Test object too large
        request.setMethod("GET");
        response.setStatusCode(200);
        response.setBody(new byte[600]); // Larger than 512 byte limit
        assertFalse(cache.isCacheable(request, response));
    }
    
    @Test
    public void testBasicCacheOperations() {
        // Setup cacheable request/response
        request.setMethod("GET");
        request.setTarget("http://example.com/");
        response.setStatusCode(200);
        response.setBody("Hello World".getBytes());
        
        String cacheKey = cache.normalizeUrl(request.getTarget());
        
        // Initially empty
        assertNull(cache.get(cacheKey));
        
        // Put in cache
        assertTrue(cache.put(cacheKey, response, request));
        
        // Retrieve from cache
        HTTPResponse cached = cache.get(cacheKey);
        assertNotNull(cached);
        assertEquals(200, cached.getStatusCode());
        assertArrayEquals("Hello World".getBytes(), cached.getBody());
    }
    
    @Test
    public void testLRUEviction() {
        // Create responses that fill the cache
        String[] urls = {
            "http://example.com/1",
            "http://example.com/2", 
            "http://example.com/3"
        };
        
        // Each response is 300 bytes, cache limit is 1024 bytes
        byte[] data = new byte[300];
        
        // Add first two items (600 bytes total)
        for (int i = 0; i < 2; i++) {
            request.setMethod("GET");
            request.setTarget(urls[i]);
            response.setStatusCode(200);
            response.setBody(data);
            
            String cacheKey = cache.normalizeUrl(urls[i]);
            assertTrue(cache.put(cacheKey, response, request));
            assertNotNull(cache.get(cacheKey));
        }
        
        // Add third item (900 bytes total) - should still fit
        request.setTarget(urls[2]);
        response.setBody(data);
        String cacheKey3 = cache.normalizeUrl(urls[2]);
        assertTrue(cache.put(cacheKey3, response, request));
        
        // All three should still be in cache
        for (String url : urls) {
            assertNotNull("URL " + url + " should be in cache", 
                         cache.get(cache.normalizeUrl(url)));
        }
        
        // Add a larger item that requires eviction
        byte[] largeData = new byte[400];
        request.setTarget("http://example.com/large");
        response.setBody(largeData);
        String largeCacheKey = cache.normalizeUrl(request.getTarget());
        assertTrue(cache.put(largeCacheKey, response, request));
        
        // First item should be evicted (LRU)
        assertNull("First item should be evicted", 
                  cache.get(cache.normalizeUrl(urls[0])));
        
        // Large item should be in cache
        assertNotNull(cache.get(largeCacheKey));
    }
    
    @Test
    public void testCacheStatistics() {
        String url = "http://example.com/test";
        String cacheKey = cache.normalizeUrl(url);
        
        // Initial stats
        HTTPCache.CacheStats stats = cache.getStats();
        assertEquals(0, stats.entries);
        assertEquals(0, stats.size);
        assertEquals(0, stats.hits);
        assertEquals(0, stats.misses);
        assertEquals(0.0, stats.hitRate, 0.001);
        
        // Cache miss
        assertNull(cache.get(cacheKey));
        stats = cache.getStats();
        assertEquals(1, stats.misses);
        assertEquals(0.0, stats.hitRate, 0.001);
        
        // Add to cache
        request.setMethod("GET");
        request.setTarget(url);
        response.setStatusCode(200);
        response.setBody("test data".getBytes());
        cache.put(cacheKey, response, request);
        
        stats = cache.getStats();
        assertEquals(1, stats.entries);
        assertEquals(9, stats.size); // "test data".length()
        
        // Cache hit
        assertNotNull(cache.get(cacheKey));
        stats = cache.getStats();
        assertEquals(1, stats.hits);
        assertEquals(1, stats.misses);
        assertEquals(0.5, stats.hitRate, 0.001);
        
        // Another hit
        assertNotNull(cache.get(cacheKey));
        stats = cache.getStats();
        assertEquals(2, stats.hits);
        assertEquals(1, stats.misses);
        assertEquals(2.0/3.0, stats.hitRate, 0.001);
    }
    
    @Test
    public void testCacheClear() {
        // Add some items
        for (int i = 0; i < 3; i++) {
            request.setMethod("GET");
            request.setTarget("http://example.com/" + i);
            response.setStatusCode(200);
            response.setBody(("data" + i).getBytes());
            
            String cacheKey = cache.normalizeUrl(request.getTarget());
            cache.put(cacheKey, response, request);
        }
        
        HTTPCache.CacheStats stats = cache.getStats();
        assertTrue(stats.entries > 0);
        assertTrue(stats.size > 0);
        
        // Clear cache
        cache.clear();
        
        stats = cache.getStats();
        assertEquals(0, stats.entries);
        assertEquals(0, stats.size);
        
        // Verify items are gone
        for (int i = 0; i < 3; i++) {
            String cacheKey = cache.normalizeUrl("http://example.com/" + i);
            assertNull(cache.get(cacheKey));
        }
    }
    
    @Test
    public void testObjectTooLarge() {
        request.setMethod("GET");
        request.setTarget("http://example.com/large");
        response.setStatusCode(200);
        response.setBody(new byte[600]); // Larger than 512 byte limit
        
        String cacheKey = cache.normalizeUrl(request.getTarget());
        assertFalse(cache.put(cacheKey, response, request));
        assertNull(cache.get(cacheKey));
    }
    
    @Test
    public void testCacheOverwrite() {
        String url = "http://example.com/test";
        String cacheKey = cache.normalizeUrl(url);
        
        // Add first version
        request.setMethod("GET");
        request.setTarget(url);
        response.setStatusCode(200);
        response.setBody("version1".getBytes());
        cache.put(cacheKey, response, request);
        
        HTTPResponse cached = cache.get(cacheKey);
        assertArrayEquals("version1".getBytes(), cached.getBody());
        
        // Overwrite with second version
        response.setBody("version2".getBytes());
        cache.put(cacheKey, response, request);
        
        cached = cache.get(cacheKey);
        assertArrayEquals("version2".getBytes(), cached.getBody());
        
        // Cache size should reflect the overwrite
        HTTPCache.CacheStats stats = cache.getStats();
        assertEquals(1, stats.entries);
        assertEquals(8, stats.size); // "version2".length()
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        // Basic thread safety test
        final String url = "http://example.com/concurrent";
        final String cacheKey = cache.normalizeUrl(url);
        
        // Setup response
        request.setMethod("GET");
        request.setTarget(url);
        response.setStatusCode(200);
        response.setBody("concurrent test".getBytes());
        
        // Put in cache
        cache.put(cacheKey, response, request);
        
        // Create multiple threads accessing cache
        Thread[] threads = new Thread[10];
        final boolean[] success = new boolean[threads.length];
        
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    // Multiple gets and stats calls
                    for (int j = 0; j < 100; j++) {
                        HTTPResponse result = cache.get(cacheKey);
                        if (result != null && result.getStatusCode() == 200) {
                            cache.getStats();
                        }
                    }
                    success[index] = true;
                } catch (Exception e) {
                    success[index] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            thread.join(5000); // 5 second timeout
        }
        
        // Check all threads completed successfully
        for (boolean result : success) {
            assertTrue("Thread safety test failed", result);
        }
    }
}
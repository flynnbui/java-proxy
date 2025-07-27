package com.comp3331.proxy.cache;

import com.comp3331.proxy.http.HTTPRequest;
import com.comp3331.proxy.http.HTTPResponse;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe LRU cache for HTTP responses.
 */
public class HTTPCache {
    private final int maxCacheSize;
    private final int maxObjectSize;
    private final LinkedHashMap<String, CacheItem> cache;
    private int currentSize;
    private int hitCount;
    private int missCount;
    private final ReentrantReadWriteLock lock;
    
    public HTTPCache(int maxCacheSize, int maxObjectSize) {
        this.maxCacheSize = maxCacheSize;
        this.maxObjectSize = maxObjectSize;
        this.cache = new LinkedHashMap<>(16, 0.75f, true); // access-order for LRU
        this.currentSize = 0;
        this.hitCount = 0;
        this.missCount = 0;
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * Normalize URL for consistent cache keys.
     */
    public String normalizeUrl(String url) {
        try {
            URI uri = new URI(url);
            
            // Normalize components
            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "http";
            String hostname = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            
            // Handle default ports
            int port = uri.getPort();
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            
            // Path normalization (empty path becomes '/')
            String path = uri.getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            
            // Query is case sensitive
            String query = uri.getQuery();
            
            // Reconstruct normalized URL
            StringBuilder normalized = new StringBuilder();
            normalized.append(scheme).append("://").append(hostname);
            
            // Only include port if not default
            if (("http".equals(scheme) && port != 80) || ("https".equals(scheme) && port != 443)) {
                normalized.append(":").append(port);
            }
            
            normalized.append(path);
            
            if (query != null) {
                normalized.append("?").append(query);
            }
            
            return normalized.toString();
            
        } catch (URISyntaxException e) {
            // If parsing fails, return original URL
            return url;
        }
    }
    
    /**
     * Check if response is cacheable based on assignment rules.
     */
    public boolean isCacheable(HTTPRequest request, HTTPResponse response) {
        // Only GET requests
        if (!"GET".equals(request.getMethod())) {
            return false;
        }
        
        // Only 200 OK responses
        if (response.getStatusCode() != 200) {
            return false;
        }
        
        // Check object size
        int objectSize = response.getBody() != null ? response.getBody().length : 0;
        if (objectSize > maxObjectSize) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Thread-safe get cached response (moves to end for LRU).
     */
    public HTTPResponse get(String cacheKey) {
        lock.readLock().lock();
        try {
            CacheItem item = cache.get(cacheKey);
            if (item != null) {
                hitCount++;
                return item.response;
            }
            
            missCount++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Thread-safe add response to cache with LRU eviction.
     */
    public boolean put(String cacheKey, HTTPResponse response, HTTPRequest request) {
        int objectSize = response.getBody() != null ? response.getBody().length : 0;
        
        // Check if object fits in cache
        if (objectSize > maxObjectSize) {
            return false;
        }
        
        lock.writeLock().lock();
        try {
            // Remove existing entry if present
            CacheItem existing = cache.remove(cacheKey);
            if (existing != null) {
                currentSize -= existing.size;
            }
            
            // Evict items if necessary to make space
            makeSpace(objectSize);
            
            // Store in cache
            CacheItem cacheItem = new CacheItem(response, objectSize, System.currentTimeMillis(), request.getTarget());
            
            // Add new entry
            cache.put(cacheKey, cacheItem);
            currentSize += objectSize;
            
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Evict LRU items to make space for new object.
     */
    private void makeSpace(int requiredSize) {
        while (currentSize + requiredSize > maxCacheSize && !cache.isEmpty()) {
            // Remove least recently used (first item in LinkedHashMap)
            Map.Entry<String, CacheItem> lruEntry = cache.entrySet().iterator().next();
            String lruKey = lruEntry.getKey();
            CacheItem lruItem = lruEntry.getValue();
            
            cache.remove(lruKey);
            currentSize -= lruItem.size;
            
            System.out.println("Cache evicted: " + lruKey + " (" + lruItem.size + " bytes)");
        }
    }
    
    /**
     * Thread-safe get cache statistics.
     */
    public CacheStats getStats() {
        lock.readLock().lock();
        try {
            double hitRate = (hitCount + missCount) > 0 ? 
                (double) hitCount / (hitCount + missCount) : 0.0;
            
            return new CacheStats(cache.size(), currentSize, hitCount, missCount, hitRate);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Thread-safe clear all cache entries.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            currentSize = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Cache item wrapper.
     */
    private static class CacheItem {
        final HTTPResponse response;
        final int size;
        final long timestamp;
        final String requestTarget; // For debugging
        
        CacheItem(HTTPResponse response, int size, long timestamp, String requestTarget) {
            this.response = response;
            this.size = size;
            this.timestamp = timestamp;
            this.requestTarget = requestTarget;
        }
    }
    
    /**
     * Cache statistics holder.
     */
    public static class CacheStats {
        public final int entries;
        public final int size;
        public final int hits;
        public final int misses;
        public final double hitRate;
        
        public CacheStats(int entries, int size, int hits, int misses, double hitRate) {
            this.entries = entries;
            this.size = size;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStats{entries=%d, size=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                entries, size, hits, misses, hitRate * 100);
        }
    }
}
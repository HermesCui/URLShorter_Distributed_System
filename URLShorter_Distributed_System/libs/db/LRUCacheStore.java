package libs.db;

import java.util.LinkedHashMap;
import java.util.Map;

public class LRUCacheStore {
    private final int cacheSize;
    private final LinkedHashMap<String, String> cache;

    public LRUCacheStore(int size) {
        this.cacheSize = size;
        this.cache = new LinkedHashMap<String, String>(size, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > cacheSize;
            }
        };
    }

    // Retrieve an item from the cache
    public String get(String shortURL) {
        return cache.getOrDefault(shortURL, null);
    }

    // Add or update an item in the cache
    public void put(String shortURL, String longURL) {
        cache.put(shortURL, longURL);
    }

    // Check if the cache contains a specific short URL
    public boolean containsKey(String shortURL) {
        return cache.containsKey(shortURL);
    }
}

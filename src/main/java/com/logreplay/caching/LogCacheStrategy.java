package com.logreplay.caching;

/**
 * Strategy interface for caching and retrieving original log messages.
 * This allows us to swap implementations (e.g., In-Memory vs. Memory-Mapped
 * File)
 * without changing the core matching logic.
 */
public interface LogCacheStrategy {

    /**
     * initializes the cache from the source file.
     * 
     * @param filePath Absolute path to the original log file.
     */
    void indexFile(String filePath);

    /**
     * Retrieves the full log line for a given Order ID.
     * 
     * @param orderId Tag 37 or Tag 11
     * @return The raw log line, or null if not found.
     */
    String getMessage(String orderId);
}

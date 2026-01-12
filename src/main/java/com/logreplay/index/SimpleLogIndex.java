package com.logreplay.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple In-Memory Log Index
 * 
 * Strategy:
 * - Load all original log messages into a HashMap.
 * - Key: OrderID (e.g., "ORDER123")
 * - Value: The full FIX message content
 * 
 * Pros: Simple, fast O(1) lookups, easy to debug.
 * Cons: Higher memory usage (stores full file in RAM).
 */
public class SimpleLogIndex {

    // Thread-safe map to store messages
    private final Map<String, String> messageMap = new ConcurrentHashMap<>();
    private final String filePath;
    private final String indexName;

    public SimpleLogIndex(String name, String filePath) {
        this.indexName = name;
        this.filePath = filePath;
        buildIndex();
    }

    private void buildIndex() {
        System.out.println("[" + indexName + "] Loading log file into memory: " + filePath);
        long start = System.currentTimeMillis();
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // simple validation
                if (!line.contains("8=FIX"))
                    continue;

                // Normalize: Convert pipe '|' to strict SOH '\u0001' for comparison
                line = line.replace('|', '\u0001');

                String orderId = extractOrderId(line);
                if (orderId != null) {
                    messageMap.put(orderId, line);
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("[" + indexName + "] Failed to load log file: " + e.getMessage());
        }

        long time = System.currentTimeMillis() - start;
        System.out.println("[" + indexName + "] Loaded " + count + " orders in " + time + "ms");
    }

    /**
     * Extracts OrderID (Tag 37) or ClOrdID (Tag 11) from a FIX message string.
     */
    private String extractOrderId(String line) {
        // Look for Tag 37 (OrderID) first
        String id = getTagValue(line, "37=");
        if (id != null)
            return id;

        // Fallback to Tag 11 (ClOrdID)
        return getTagValue(line, "11=");
    }

    /**
     * Helper to parse "37=RX123^A"
     */
    private String getTagValue(String line, String tagKey) {
        // Finding, for example, "^A37=" ensures we match the tag start
        String searchKey = "\u0001" + tagKey; // SOH + Tag
        int start = line.indexOf(searchKey);

        // Also ensure we handle the first tag in the message which might not have SOH
        // before it
        if (start == -1 && line.startsWith(tagKey)) {
            start = 0; // Special case: tag is at start of line
            searchKey = tagKey;
        }

        if (start != -1) {
            start += searchKey.length();
            int end = line.indexOf('\u0001', start); // Find next SOH
            if (end != -1) {
                return line.substring(start, end);
            }
        }
        return null;
    }

    public String getMessage(String orderId) {
        return messageMap.get(orderId);
    }

    /**
     * Removes the message after processing to free up memory slot.
     */
    public void remove(String orderId) {
        messageMap.remove(orderId);
    }

    public int size() {
        return messageMap.size();
    }
}

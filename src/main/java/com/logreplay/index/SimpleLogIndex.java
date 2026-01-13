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
        System.out.println(">> [" + indexName + "] STARTING INDEX BUILD...");
        long start = System.currentTimeMillis();
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // simple validation
                if (!line.contains("8=FIX"))
                    continue;

                // DEBUG: Raw Line
                // System.out.println("[INDEX] Raw Line: " + line);

                // 1. Normalize: The screenshot shows literal "^A" characters.
                // We MUST convert these to strict SOH '\u0001'.
                String normalized = line.replace("^A", "\u0001");

                // DEBUG: Normalized
                // System.out.println("[INDEX] Norm Line: " + normalized.replace('\u0001',
                // '|'));

                // 2. Extract Key.
                // User Request: "VOD.L". Screenshot shows "55=VOD.L".
                // We will extract Tag 55.
                String orderId = extractOrderId(normalized); // This will look for 55=

                if (orderId != null) {
                    if (orderId.equals("VOD.L")) {
                        System.out.println(">> [INDEX CHECK] FOUND VOD.L at Tag 55! Storing...");
                    }
                    messageMap.put(orderId, normalized);
                    count++;
                } else {
                    // System.out.println("[INDEX] SKIPPING: No Key found.");
                }
            }
        } catch (IOException e) {
            System.err.println("[" + indexName + "] Failed to load log file: " + e.getMessage());
        }

        long time = System.currentTimeMillis() - start;
        System.out.println(">> [" + indexName + "] INDEX READY. Loaded " + count + " orders in " + time + "ms");
    }

    /**
     * Extracts Tag 55 (Symbol) based on Visual Inspection of 'VOD.L'
     */
    private String extractOrderId(String line) {
        // Look for Tag 55 (Symbol)
        return getTagValue(line, "55=");
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

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
        System.out.println(">> [" + indexName + "] STARTING INDEX BUILD (Stream Mode)...");
        long start = System.currentTimeMillis();
        int count = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            // 1. Read ENTIRE file into memory to handle any weird line-breaks or
            // concatenations
            StringBuilder fileBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                fileBuffer.append(line).append('\n'); // Preserve line breaks as whitespace
            }

            // 2. Normalize Globally: Handle literal "^A" and Pipe "|"
            // Converting to Standard SOH usually \u0001
            String rawData = fileBuffer.toString().replace("^A", "\u0001").replace("|", "\u0001");

            // 3. Scan for "8=FIX" (Start of Message)
            int cursor = 0;
            while (true) {
                int startPos = rawData.indexOf("8=FIX", cursor);
                if (startPos == -1)
                    break; // No more messages

                // Find start of NEXT message to determine end of CURRENT message
                int nextPos = rawData.indexOf("8=FIX", startPos + 1);

                String messageChunk;
                if (nextPos == -1) {
                    // Last message
                    messageChunk = rawData.substring(startPos);
                    cursor = rawData.length();
                } else {
                    messageChunk = rawData.substring(startPos, nextPos);
                    cursor = nextPos;
                }

                // 4. Process the Chunk
                // Clean up any trailing newlines/garbage from the file read
                String cleanMsg = messageChunk.trim();
                processLine(cleanMsg, count);
                count++;
            }

        } catch (IOException e) {
            System.err.println("[" + indexName + "] Failed to load log file: " + e.getMessage());
        }

        long time = System.currentTimeMillis() - start;
        System.out.println(">> [" + indexName + "] INDEX READY. Loaded " + messageMap.size() + " unique orders (Parsed "
                + count + " msgs) in " + time + "ms");
    }

    private void processLine(String normalizedMsg, int msgIndex) {
        // 2. Extract Key (Tag 55 = Symbol, Tag 48 = SecID, or Tag 11 = ClOrdID)
        // User specific request: "VOD.L" found in Tag 55.
        // We will try multiple keys if needed, but prioritize 55 for now.
        String orderId = extractOrderId(normalizedMsg);

        if (orderId != null) {
            // Debug check for the specific user case
            if (orderId.equals("VOD.L")) {
                System.out.println(">> [INDEX CHECK] FOUND VOD.L at Tag 55 in Msg #" + msgIndex + "! Storing...");
            }
            messageMap.put(orderId, normalizedMsg);
        } else {
            // Optional: Print warning only if strictly needed to avoid noise
            // System.out.println("[INDEX] SKIPPING Msg #" + msgIndex + ": No Tag 55
            // found.");
        }
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

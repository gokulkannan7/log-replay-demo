package com.logreplay.index;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.File;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
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
        File logFile = new File(filePath);
        System.out.println(">> [INDEX INFO] Reading from ABSOLUTE PATH: " + logFile.getAbsolutePath());

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

            // VISUALIZATION: Print table of what we are storing
            printMessageTable(normalizedMsg, orderId, msgIndex);

            messageMap.put(orderId, normalizedMsg);
        } else {
            // Optional: Print warning only if strictly needed to avoid noise
            // System.out.println("[INDEX] SKIPPING Msg #" + msgIndex + ": No Tag 55
            // found.");
        }
    }

    /**
     * Prints a beautiful table of the message content
     */
    private void printMessageTable(String msg, String id, int index) {
        System.out.println("\n>> [INDEX PARSED Msg #" + index + "] Key: " + id);
        System.out.println("-------------------------------------------------------------");
        System.out.println(String.format("| %-6s | %-50s |", "TAG", "VALUE"));
        System.out.println("-------------------------------------------------------------");

        String[] parts = msg.split("\u0001");
        List<String[]> rows = new ArrayList<>();

        for (String part : parts) {
            if (part.trim().isEmpty())
                continue;
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq);
                String val = part.substring(eq + 1);
                // DEBUG: Print weird tags to console immediately
                if (key.startsWith("-")) {
                    System.out.println(">> [INDEX DEBUG] Found Negative Tag: " + key + " = " + val);
                }
                rows.add(new String[] { key, val });
            } else {
                // System.out.println(">> [INDEX DEBUG] Ignored Token (No =): " + part);
            }
        }

        // Sort tags nicely
        Collections.sort(rows, (a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a[0]), Integer.parseInt(b[0]));
            } catch (Exception e) {
                return a[0].compareTo(b[0]);
            }
        });

        for (String[] row : rows) {
            // Truncate value if too long to keep table clean
            String val = row[1];
            if (val.length() > 50)
                val = val.substring(0, 47) + "...";
            System.out.println(String.format("| %-6s | %-50s |", row[0], val));
        }
        System.out.println("-------------------------------------------------------------\n");
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

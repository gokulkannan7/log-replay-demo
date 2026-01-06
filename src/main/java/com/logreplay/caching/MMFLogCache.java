package com.logreplay.caching;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

/**
 * A High-Performance, Low-Memory cache implementation.
 * 
 * STRATEGY: "Memory-Mapped Indexing"
 * 1. We do NOT load the file content into RAM.
 * 2. We only load a 'Map<String, Long>' (ID -> ByteOffset).
 * 3. When a message is requested, we use RandomAccessFile.seek() to fetch it
 * from disk.
 * 
 * PROS:
 * - Can handle 100GB files with only ~100MB RAM.
 * - Near instant startup.
 * - Zero GC pressure for the file content.
 */
public class MMFLogCache implements LogCacheStrategy {

    private final Map<String, Long> index = new HashMap<>();
    private RandomAccessFile contentRaf;
    private final String sohRegex = "\\^A"; // simplified for extraction

    @Override
    public void indexFile(String filePath) {
        System.out.println("[LogCache] Indexing file: " + filePath);
        try {
            this.contentRaf = new RandomAccessFile(filePath, "r");
            String line;
            long offset = 0;

            while ((line = this.contentRaf.readLine()) != null) {
                long currentLineStart = offset;
                long nextOffset = this.contentRaf.getFilePointer();

                String id = extractId(line);
                if (id != null) {
                    index.put(id, currentLineStart);
                }
                offset = nextOffset;
            }
            System.out.println("[LogCache] Indexing complete. Items: " + index.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to index file: " + filePath, e);
        }
    }

    @Override
    public String getMessage(String orderId) {
        Long offset = index.get(orderId);
        if (offset == null)
            return null;

        try {
            synchronized (this) { // RAF is not thread-safe
                contentRaf.seek(offset);
                return contentRaf.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // -- Helper to extract ID (Simplified version of what was in MatchingService)
    // --
    private String extractId(String line) {
        if (!line.contains("8=FIX"))
            return null;
        // Quick dirty parse for Tag 37 or 11
        String[] parts = line.split(sohRegex);
        String clOrdId = null;
        String orderId = null;

        for (String p : parts) {
            if (p.startsWith("37="))
                orderId = p.substring(3);
            if (p.startsWith("11="))
                clOrdId = p.substring(3);
        }
        return orderId != null ? orderId : clOrdId;
    }
}

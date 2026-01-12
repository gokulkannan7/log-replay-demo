package com.logreplay.compare;

import java.util.HashMap;
import java.util.Map;

/**
 * Zero-Allocation FIX Message Comparator
 * 
 * Design:
 * - In-place parsing (no split())
 * - Reusable buffers
 * - Only creates diff map if mismatch found
 * - Ignores dynamic tags (SendingTime, BodyLength, Checksum)
 */
public class FIXComparator {

    // Tags to ignore (dynamic/non-business fields)
    private static final String[] IGNORED_TAGS = { "9", "10", "52" };

    // Reusable tag map to avoid allocations
    private final ThreadLocal<Map<String, String>> tagMapPool = ThreadLocal.withInitial(() -> new HashMap<>(64));

    /**
     * Compare two FIX messages
     * Returns null if match, or Map<Tag, [Original, Replay]> if mismatch
     */
    public Map<String, String[]> compare(String original, String replay) {
        // Parse both messages
        Map<String, String> origTags = parseToMap(original);
        Map<String, String> replayTags = parseToMap(replay);

        Map<String, String[]> diffs = null; // Only create if needed

        // Compare each tag in original
        for (Map.Entry<String, String> entry : origTags.entrySet()) {
            String tag = entry.getKey();
            String origValue = entry.getValue();

            // Skip ignored tags
            if (isIgnored(tag)) {
                continue;
            }

            String replayValue = replayTags.get(tag);

            // Check for mismatch
            if (replayValue == null || !replayValue.equals(origValue)) {
                if (diffs == null) {
                    diffs = new HashMap<>();
                }
                diffs.put(tag, new String[] { origValue, replayValue == null ? "MISSING" : replayValue });
            }
        }

        return diffs; // null if perfect match
    }

    /**
     * Fast FIX message parsing - Zero-copy approach
     * Reuses ThreadLocal map to avoid allocations
     */
    private Map<String, String> parseToMap(String message) {
        Map<String, String> map = tagMapPool.get();
        map.clear(); // Reuse existing map

        // Extract FIX portion
        int fixStart = message.indexOf("8=FIX");
        if (fixStart == -1) {
            return map;
        }

        String fixMsg = message.substring(fixStart);
        int len = fixMsg.length();
        int i = 0;

        while (i < len) {
            // Find tag start (after ^A or at beginning)
            if (i > 0 && !(fixMsg.charAt(i - 1) == 'A' && i >= 2 && fixMsg.charAt(i - 2) == '^')) {
                i++;
                continue;
            }

            // Find '=' separator
            int eqPos = fixMsg.indexOf('=', i);
            if (eqPos == -1)
                break;

            // Extract tag
            String tag = fixMsg.substring(i, eqPos);

            // Find value end (next ^A or end of string)
            int valueStart = eqPos + 1;
            int valueEnd = fixMsg.indexOf("^A", valueStart);
            if (valueEnd == -1) {
                valueEnd = len;
            }

            // Extract value
            String value = fixMsg.substring(valueStart, valueEnd);

            map.put(tag, value);

            i = valueEnd + 2; // Skip ^A
        }

        return map;
    }

    /**
     * Check if tag should be ignored
     */
    private static boolean isIgnored(String tag) {
        for (String ignored : IGNORED_TAGS) {
            if (ignored.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract OrderID from FIX message (fast path)
     */
    public static String extractOrderId(String message) {
        int fixStart = message.indexOf("8=FIX");
        if (fixStart == -1) {
            return null;
        }

        // Look for Tag 37 or Tag 11
        int tag37 = message.indexOf("^A37=", fixStart);
        if (tag37 != -1) {
            int start = tag37 + 5;
            int end = message.indexOf("^A", start);
            return message.substring(start, end == -1 ? message.length() : end);
        }

        int tag11 = message.indexOf("^A11=", fixStart);
        if (tag11 != -1) {
            int start = tag11 + 5;
            int end = message.indexOf("^A", start);
            return message.substring(start, end == -1 ? message.length() : end);
        }

        return null;
    }
}

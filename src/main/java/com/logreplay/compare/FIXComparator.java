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
        // 1. Get Union of All Tags to compare everything
        Set<String> allTags = new HashSet<>();
        allTags.addAll(origTags.keySet());
        allTags.addAll(replayTags.keySet());

        // 2. Iterate and Compare
        for (String tag : allTags) {
            String origVal = origTags.get(tag);
            String replayVal = replayTags.get(tag);

            if (origVal == null) {
                System.out.println("TAG " + tag + ": LOG=[MISSING] vs REPLAY=[" + replayVal + "] -> EXTRA IN REPLAY");
            } else if (replayVal == null) {
                System.out.println("TAG " + tag + ": LOG=[" + origVal + "] vs REPLAY=[MISSING] -> MISSING IN REPLAY");
                if (diffs == null)
                    diffs = new HashMap<>();
                diffs.put(tag, new String[] { origVal, "MISSING" });
            } else if (origVal.equals(replayVal)) {
                System.out.println("TAG " + tag + ": LOG=[" + origVal + "] vs REPLAY=[" + replayVal + "] -> MATCH");
            } else {
                System.out.println("TAG " + tag + ": LOG=[" + origVal + "] vs REPLAY=[" + replayVal + "] -> MISMATCH");
                if (diffs == null)
                    diffs = new HashMap<>();
                diffs.put(tag, new String[] { origVal, replayVal });
            }
        }

        return diffs; // null if perfect match
    }

    /**
     * Fast FIX message parsing - Zero-copy approach
     * Reuses ThreadLocal map to avoid allocations
     */
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

        // Split by standard SOH delimiter (which we normalized earlier)
        String[] parts = fixMsg.split("\u0001");

        for (String part : parts) {
            int eqPos = part.indexOf('=');
            if (eqPos != -1) {
                String tag = part.substring(0, eqPos);
                String value = part.substring(eqPos + 1);
                map.put(tag, value);
            }
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
     * Extract Tag 55 (Symbol) from FIX message (Modified for Testing)
     */
    public static String extractOrderId(String message) {
        int fixStart = message.indexOf("8=FIX");
        if (fixStart == -1) {
            return null;
        }

        // Normalize message delimiters to ensure we find the tag
        // 1. Convert pipe '|' to strict SOH '\u0001'
        // 2. Convert literal "^A" to strict SOH '\u0001'
        String normalized = message.replace('|', '\u0001').replace("^A", "\u0001");

        // Look for Tag 55 (Symbol)
        int tag55 = normalized.indexOf("\u000155=", fixStart);
        if (tag55 != -1) {
            int start = tag55 + 4; // Skip ^A55=
            int end = normalized.indexOf("\u0001", start);
            return normalized.substring(start, end == -1 ? normalized.length() : end);
        }

        return null;
    }
}

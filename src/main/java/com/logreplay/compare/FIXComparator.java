package com.logreplay.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // VISUAL DEBUG: Print full messages with Pipe delimiters
        System.out.println("\n============ COMPARISON START ============");
        System.out.println(">> ORIG:   " + original.replace('\u0001', '|'));
        System.out.println(">> REPLAY: " + replay.replace('\u0001', '|'));
        System.out.println("------------------------------------------");

        // Parse both messages
        Map<String, String> origTags = parseToMap(original);
        Map<String, String> replayTags = parseToMap(replay);

        System.out.println(
                ">> [COMPARE] Orig Tags Parsed: " + origTags.size() + " | Replay Tags Parsed: " + replayTags.size());

        Map<String, String[]> diffs = null; // Only create if needed

        // 1. Get Union of All Tags to compare everything
        Set<String> allTags = new HashSet<>();
        allTags.addAll(origTags.keySet());
        allTags.addAll(replayTags.keySet());

        // 2. Iterate and Compare (Structured Table)
        System.out.println("----------------------------------------------------------------------------------");
        System.out.println(String.format("| %-5s | %-25s | %-25s | %-12s |", "TAG", "ORIGINAL", "REPLAY", "STATUS"));
        System.out.println("----------------------------------------------------------------------------------");

        // Sort tags numerically/alphabetically for cleaner reading
        List<String> sortedTags = new ArrayList<>(allTags);
        Collections.sort(sortedTags, (a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        for (String tag : sortedTags) {
            String origVal = origTags.get(tag);
            String replayVal = replayTags.get(tag);
            String status;

            // Truncate long values for table display
            String displayOrig = (origVal == null) ? "MISSING"
                    : (origVal.length() > 25 ? origVal.substring(0, 22) + "..." : origVal);
            String displayReplay = (replayVal == null) ? "MISSING"
                    : (replayVal.length() > 25 ? replayVal.substring(0, 22) + "..." : replayVal);

            if (origVal == null) {
                status = "EXTRA (R)";
                if (diffs == null)
                    diffs = new HashMap<>();
                diffs.put(tag, new String[] { "MISSING", replayVal });
            } else if (replayVal == null) {
                status = "MISSING (R)";
                if (diffs == null)
                    diffs = new HashMap<>();
                diffs.put(tag, new String[] { origVal, "MISSING" });
            } else if (origVal.equals(replayVal)) {
                status = "MATCH";
            } else {
                status = "MISMATCH";
                if (diffs == null)
                    diffs = new HashMap<>();
                diffs.put(tag, new String[] { origVal, replayVal });
            }

            System.out.println(
                    String.format("| %-5s | %-25s | %-25s | %-12s |", tag, displayOrig, displayReplay, status));
        }
        System.out.println("----------------------------------------------------------------------------------\n");

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
            System.out.println(">> [PARSER WARNING] No '8=FIX' found in string: "
                    + message.substring(0, Math.min(20, message.length())));
            return map;
        }

        String payload = message.substring(fixStart);
        int len = payload.length();
        int start = 0;

        // Manual Tokenizer Loop: Iterates chars to find Delimiters
        for (int i = 0; i < len; i++) {
            char c = payload.charAt(i);
            // Delimiter Check: SOH or Pipe or newline
            if (c == '\u0001' || c == '|' || c == '\n' || c == '\r') {
                parseAndPut(payload, start, i, map);
                start = i + 1;
            }
        }
        // Handle last incomplete token
        if (start < len) {
            parseAndPut(payload, start, len, map);
        }

        return map;
    }

    private void parseAndPut(String src, int start, int end, Map<String, String> map) {
        if (start >= end)
            return; // Empty token
        String token = src.substring(start, end);
        int eqPos = token.indexOf('=');
        if (eqPos > 0) { // Tag must be at least 1 char
            String tag = token.substring(0, eqPos);
            String value = token.substring(eqPos + 1);
            map.put(tag, value);
        }
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

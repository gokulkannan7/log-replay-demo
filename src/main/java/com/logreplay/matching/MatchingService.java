package com.logreplay.matching;

import com.logreplay.caching.LogCacheStrategy;
import com.logreplay.caching.MMFLogCache;
import com.logreplay.source.SolaceReceiver;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class MatchingService {

    private static final String LOG_PREFIX_REGEX = ".*parse: ";
    private static final String SOH = "^A";

    // -- Inner Class for Result --
    public static class ComparisonResult {
        public String streamName; // OMS or ONC
        public String orderId;
        public String status;
        public Map<String, String[]> tagMismatches = new HashMap<>();

        public ComparisonResult(String streamName, String orderId) {
            this.streamName = streamName;
            this.orderId = orderId;
        }
    }

    // -- Main Entry Point --
    public static void startEngine(Consumer<ComparisonResult> resultEmitter) {
        System.out.println(">>> STARTING LOG REPLAY VERIFICATION ENGINE <<<");

        // 1. Initialize Caches for ORIGINAL logs (The Truth)
        // We use the Memory-Mapped File Strategy (Best for Large Logs)
        LogCacheStrategy cacheOMS = new MMFLogCache();
        LogCacheStrategy cacheONC = new MMFLogCache();

        // In a real scenario, correct paths would be passed here
        cacheOMS.indexFile("logs/OneOmsFixSrcOriginal.log");
        cacheONC.indexFile("logs/OneOncFixSrcOriginal.log");

        // 2. Start Solace Consumers (The Replay Stream)
        // We define a callback that executes logic whenever a message arrives
        startSolaceListener("OMS", "topic_oms", cacheOMS, resultEmitter);
        startSolaceListener("ONC", "topic_onc", cacheONC, resultEmitter);
    }

    private static void startSolaceListener(String streamName, String topicKey, LogCacheStrategy cache,
            Consumer<ComparisonResult> emitter) {
        new SolaceReceiver("solace.properties", topicKey, (replayMsg) -> {
            try {
                processMessage(streamName, replayMsg, cache, emitter);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static void processMessage(String streamName, String replayLine, LogCacheStrategy cache,
            Consumer<ComparisonResult> emitter) {
        String id = extractIdFromLine(replayLine);
        if (id == null)
            return; // Skip non-fix lines

        ComparisonResult result = new ComparisonResult(streamName, id);

        // A. Fetch Original from Cache
        String originalLine = cache.getMessage(id);

        if (originalLine == null) {
            result.status = "MISSING_IN_ORIGINAL";
            emitter.accept(result);
            return;
        }

        // B. Compare
        compareLines(originalLine, replayLine, result);

        // C. Emit Result (Only Mismatches or Valid matches if desired)
        emitter.accept(result);
    }

    // -- Comparison Logic (Same as before, just adapted) --
    private static void compareLines(String origLine, String replayLine, ComparisonResult result) {
        Map<String, String> origMap = parseFix(extractFixMessage(origLine));
        Map<String, String> replayMap = parseFix(extractFixMessage(replayLine));

        boolean mismatch = false;

        for (Map.Entry<String, String> entry : origMap.entrySet()) {
            String tag = entry.getKey();
            String val = entry.getValue();

            if (isIgnoredTag(tag))
                continue;

            if (!replayMap.containsKey(tag)) {
                result.tagMismatches.put(tag, new String[] { val, "MISSING" });
                mismatch = true;
            } else if (!replayMap.get(tag).equals(val)) {
                result.tagMismatches.put(tag, new String[] { val, replayMap.get(tag) });
                mismatch = true;
            }
        }

        result.status = mismatch ? "MISMATCH" : "MATCH";
    }

    private static String extractIdFromLine(String line) {
        String fixMsg = extractFixMessage(line);
        if (fixMsg == null)
            return null;
        Map<String, String> tags = parseFix(fixMsg);
        return tags.getOrDefault("37", tags.getOrDefault("11", null));
    }

    private static String extractFixMessage(String line) {
        String[] parts = line.split(LOG_PREFIX_REGEX);
        if (parts.length < 2)
            return null;
        return parts[1];
    }

    private static boolean isIgnoredTag(String tag) {
        return tag.equals("52") || tag.equals("9") || tag.equals("10");
    }

    private static Map<String, String> parseFix(String fixMsg) {
        Map<String, String> tags = new HashMap<>();
        if (fixMsg == null)
            return tags;

        String[] fields = fixMsg.split(SOH.replace("^", "\\^")); // Regex safe split
        for (String field : fields) {
            String[] kv = field.split("=");
            if (kv.length == 2) {
                tags.put(kv[0], kv[1]);
            }
        }
        return tags;
    }
}
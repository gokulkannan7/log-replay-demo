package com.logreplay.matching;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MatchingService {

    private static final String LOG_PREFIX_REGEX = ".*parse: ";
    private static final String SOH = "^A";

    public static class ComparisonResult {
        public String orderId;
        public String status;
        public Map<String, String[]> tagMismatches = new HashMap<>();

        public ComparisonResult(String orderId) {
            this.orderId = orderId;
        }

        @Override
        public String toString() {
            if (tagMismatches.isEmpty()) {
                return String.format("[%-20s] STATUS: %s", orderId, status);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("[%-20s] STATUS: %s\n", orderId, status));
            tagMismatches.forEach((tag, values) -> sb
                    .append(String.format("    - Tag %s: Original='%s', Replay='%s'\n", tag, values[0], values[1])));
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        String originalPath = "logs/original.log";
        String replayedPath = "logs/replayed.log";

        System.out.println("=========================================");
        System.out.println("   LOG REPLAY MATCHING SERVICE REPORT    ");
        System.out.println("=========================================");
        System.out.println("Mode: Low-Latency Offset Indexing (Optimized for Large Files)");

        List<ComparisonResult> collectedResults = new ArrayList<>();

        // Use the streaming API
        streamComparison(originalPath, replayedPath, result -> {
            collectedResults.add(result);
        });

        // Display results (excluding matches)
        Map<String, List<ComparisonResult>> groupedResults = collectedResults.stream()
                .filter(r -> !r.status.equals("MATCH"))
                .collect(Collectors.groupingBy(r -> r.status));

        groupedResults.forEach((status, list) -> {
            System.out.println("\n--- " + status + " (" + list.size() + ") ---");
            list.forEach(System.out::println);
        });

        System.out.println("\nTotal Processed: " + collectedResults.size());
    }

    /**
     * Streams comparison results one by one to the consumer.
     * This is ideal for WebSockets where we want to push updates immediately.
     */
    public static void streamComparison(String origPath, String replayPath, Consumer<ComparisonResult> observer) {
        // Phase 1: Index the ORIGINAL file only (Map ID -> ByteOffset)
        Map<String, Long> originalIndex = indexLogFile(origPath);

        // Phase 2: Stream replayed file and compare on-the-fly
        compareWithIndex(origPath, replayPath, originalIndex, observer);
    }

    private static Map<String, Long> indexLogFile(String path) {
        Map<String, Long> index = new HashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            String line;
            long offset = 0;
            while ((line = raf.readLine()) != null) {
                long currentLineStart = offset;
                long nextOffset = raf.getFilePointer();

                String id = extractIdFromLine(line);
                if (id != null) {
                    index.put(id, currentLineStart);
                }

                offset = nextOffset;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return index;
    }

    private static void compareWithIndex(String origPath, String replayPath, Map<String, Long> origIndex,
            Consumer<ComparisonResult> observer) {
        Set<String> processedIds = new HashSet<>();

        try (BufferedReader replayReader = new BufferedReader(new FileReader(replayPath));
                RandomAccessFile origRaf = new RandomAccessFile(origPath, "r")) {

            String line;
            while ((line = replayReader.readLine()) != null) {
                String id = extractIdFromLine(line);
                if (id == null)
                    continue;

                processedIds.add(id);
                ComparisonResult result = new ComparisonResult(id);

                if (!origIndex.containsKey(id)) {
                    result.status = "MISSING_IN_ORIGINAL";
                    observer.accept(result);
                    continue;
                }

                long offset = origIndex.get(id);
                origRaf.seek(offset);
                String origLine = origRaf.readLine();

                compareLines(origLine, line, result);
                observer.accept(result);
            }

            // Check for items missing in replay
            for (String origId : origIndex.keySet()) {
                if (!processedIds.contains(origId)) {
                    ComparisonResult res = new ComparisonResult(origId);
                    res.status = "MISSING_IN_REPLAY";
                    observer.accept(res);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
        return tags.getOrDefault("37", tags.get("11"));
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

        String[] fields = fixMsg.split("\\Q" + SOH + "\\E");
        for (String field : fields) {
            String[] kv = field.split("=");
            if (kv.length == 2) {
                tags.put(kv[0], kv[1]);
            }
        }
        return tags;
    }
}
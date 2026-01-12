package com.logreplay.solace;

import com.logreplay.matching.MatchingService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integration layer that connects Solace consumer to the Matching Engine
 * 
 * This class demonstrates how to:
 * 1. Start a Solace consumer
 * 2. Feed messages directly to the matching engine
 * 3. Handle both OMS and ONC streams simultaneously
 */
public class SolaceReplayIntegration {

    private SolaceLogConsumer omsConsumer;
    private SolaceLogConsumer oncConsumer;
    private final AtomicInteger totalProcessed = new AtomicInteger(0);

    /**
     * Initialize and start both OMS and ONC consumers
     */
    public void startReplayStreams(java.util.function.Consumer<MatchingService.ComparisonResult> resultHandler) {
        System.out.println("=== Starting Solace Replay Integration ===");

        try {
            // Start OMS Stream Consumer
            omsConsumer = new SolaceLogConsumer(
                    "solace.properties",
                    "topic_oms",
                    (logLine) -> {
                        processReplayMessage("OMS", logLine, resultHandler);
                    });
            omsConsumer.start();

            // Start ONC Stream Consumer
            oncConsumer = new SolaceLogConsumer(
                    "solace.properties",
                    "topic_onc",
                    (logLine) -> {
                        processReplayMessage("ONC", logLine, resultHandler);
                    });
            oncConsumer.start();

            System.out.println("=== Both streams started successfully ===");

        } catch (Exception e) {
            System.err.println("Failed to start replay streams: " + e.getMessage());
            e.printStackTrace();
            shutdown();
        }
    }

    /**
     * Process a single replay message from Solace
     */
    private void processReplayMessage(
            String streamName,
            String replayLogLine,
            java.util.function.Consumer<MatchingService.ComparisonResult> resultHandler) {
        try {
            // Extract Order ID from the replay message
            String orderId = extractOrderId(replayLogLine);
            if (orderId == null) {
                return; // Skip non-FIX messages
            }

            // TODO: Fetch original log line from cache
            // For now, this is a placeholder - you'll integrate with your cache strategy
            String originalLogLine = fetchOriginalFromCache(streamName, orderId);

            if (originalLogLine == null) {
                // Order not found in original logs
                MatchingService.ComparisonResult result = new MatchingService.ComparisonResult(orderId);
                result.status = "MISSING_IN_ORIGINAL";
                resultHandler.accept(result);
                return;
            }

            // Compare and emit result
            MatchingService.ComparisonResult result = new MatchingService.ComparisonResult(orderId);
            compareMessages(originalLogLine, replayLogLine, result);
            resultHandler.accept(result);

            totalProcessed.incrementAndGet();

        } catch (Exception e) {
            System.err.println("[" + streamName + "] Error processing message: " + e.getMessage());
        }
    }

    /**
     * Extract Order ID from FIX message
     * This is a simplified version - adapt to your log format
     */
    private String extractOrderId(String logLine) {
        if (!logLine.contains("8=FIX")) {
            return null;
        }

        // Look for Tag 37 (OrderID) or Tag 11 (ClOrdID)
        String[] parts = logLine.split("\\^A");
        for (String part : parts) {
            if (part.startsWith("37=")) {
                return part.substring(3);
            }
            if (part.startsWith("11=")) {
                return part.substring(3);
            }
        }
        return null;
    }

    /**
     * Fetch original log from your caching layer
     * TODO: Integrate with MMFLogCache or your chosen strategy
     */
    private String fetchOriginalFromCache(String streamName, String orderId) {
        // Placeholder - replace with actual cache lookup
        // Example: return cacheOMS.getMessage(orderId);
        return null;
    }

    /**
     * Compare original vs replay messages
     * TODO: Integrate with MatchingService comparison logic
     */
    private void compareMessages(String original, String replay, MatchingService.ComparisonResult result) {
        // Placeholder - use your existing comparison logic from MatchingService
        result.status = "MATCH"; // Default
    }

    /**
     * Graceful shutdown
     */
    public void shutdown() {
        System.out.println("=== Shutting down Solace Integration ===");

        if (omsConsumer != null) {
            omsConsumer.stop();
        }
        if (oncConsumer != null) {
            oncConsumer.stop();
        }

        System.out.println("Total messages processed: " + totalProcessed.get());
    }

    /**
     * Get statistics
     */
    public int getTotalProcessed() {
        return totalProcessed.get();
    }

    /**
     * Demo/Test main method
     */
    public static void main(String[] args) {
        SolaceReplayIntegration integration = new SolaceReplayIntegration();

        // Start consuming and processing
        integration.startReplayStreams(result -> {
            System.out.println("Result: " + result.orderId + " -> " + result.status);
        });

        // Add shutdown hook for graceful termination
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            integration.shutdown();
        }));

        System.out.println("Press Ctrl+C to stop...");
    }
}

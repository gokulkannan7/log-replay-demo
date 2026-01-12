package com.logreplay.solace;

import com.logreplay.compare.FIXComparator;
import com.logreplay.index.SimpleLogIndex;
// import com.logreplay.validate.source.CryptUtil; // TODO: Add your CryptUtil class
import com.solacesystems.jcsmp.*;

import java.io.FileInputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Single Stream Solace Replay Engine
 * Simplest possible implementation: 1 Log File + 1 Solace Topic
 */
public class SolaceReplayEngine {

    private final SimpleLogIndex simpleIndex;
    private final FIXComparator comparator;
    private final Consumer<ComparisonResult> resultHandler;

    private JCSMPSession session;
    private XMLMessageConsumer consumer;

    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger mismatchCount = new AtomicInteger(0);

    public static class ComparisonResult {
        public String orderId;
        public String status; // "MATCH", "MISMATCH", "MISSING_IN_ORIGINAL"
        public Map<String, String[]> tagMismatches;

        public ComparisonResult(String orderId) {
            this.orderId = orderId;
        }
    }

    /**
     * Initialize with ONE log file
     */
    public SolaceReplayEngine(String originalLogPath, Consumer<ComparisonResult> resultHandler) {
        System.out.println("[Engine] Initializing Single-Stream Engine...");

        // Build single index
        this.simpleIndex = new SimpleLogIndex("MAIN", originalLogPath);
        this.comparator = new FIXComparator();
        this.resultHandler = resultHandler;

        System.out.println("[Engine] Ready\n");
    }

    /**
     * Start consuming from ONE topic
     */
    public void start(String configFile) throws Exception {
        Properties config = new Properties();
        config.load(new FileInputStream(configFile));

        // Connection setup
        JCSMPProperties props = new JCSMPProperties();
        props.setProperty(JCSMPProperties.HOST, config.getProperty("host"));
        props.setProperty(JCSMPProperties.VPN_NAME, config.getProperty("vpnName"));
        // TODO: Use CryptUtil here
        props.setProperty(JCSMPProperties.USERNAME, config.getProperty("username"));
        props.setProperty(JCSMPProperties.PASSWORD, config.getProperty("password"));
        props.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);
        props.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);

        session = JCSMPFactory.onlyInstance().createSession(props);
        session.connect();
        System.out.println("[Engine] Connected to Solace");

        // Setup Consumer
        consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                processMessage(msg);
            }

            @Override
            public void onException(JCSMPException e) {
                System.err.println("Error: " + e.getMessage());
            }
        });

        consumer.start();

        // Subscribe to SINGLE topic
        String topicName = config.getProperty("topic_oms"); // Using OMS topic for now
        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
        session.addSubscription(topic);

        System.out.println("[Engine] Subscribed to: " + topicName);
    }

    private void processMessage(BytesXMLMessage msg) {
        try {
            // 1. Get Text
            String replayMsg = (msg instanceof TextMessage)
                    ? ((TextMessage) msg).getText()
                    : new String(((BytesMessage) msg).getData());

            // 2. Get ID
            String orderId = FIXComparator.extractOrderId(replayMsg);
            if (orderId == null)
                return;

            ComparisonResult result = new ComparisonResult(orderId);

            // 3. Lookup in HashMap
            String originalMsg = simpleIndex.getMessage(orderId);

            if (originalMsg == null) {
                result.status = "MISSING_IN_ORIGINAL";
            } else {
                // 4. Compare
                Map<String, String[]> diffs = comparator.compare(originalMsg, replayMsg);
                if (diffs == null || diffs.isEmpty()) {
                    result.status = "MATCH";
                } else {
                    result.status = "MISMATCH";
                    result.tagMismatches = diffs;
                    mismatchCount.incrementAndGet();
                }
                // Free memory
                simpleIndex.remove(orderId);
            }

            // 5. Emit & Ack
            resultHandler.accept(result);
            processedCount.incrementAndGet();
            msg.ackMessage();

        } catch (Exception e) {
            System.err.println("Processing error: " + e.getMessage());
        }
    }

    public int getProcessedCount() {
        return processedCount.get();
    }

    public int getMismatchCount() {
        return mismatchCount.get();
    }

    public int getRemaining() {
        return simpleIndex.size();
    }

    public void shutdown() {
        if (consumer != null)
            consumer.close();
        if (session != null)
            session.closeSession();
        System.out.println("Shutdown complete.");
    }
}

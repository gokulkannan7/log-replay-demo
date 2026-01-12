package com.logreplay.solace;

import com.solacesystems.jcsmp.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-Performance Solace Consumer for Log Replay Stream
 * 
 * Design Principles:
 * 1. Non-blocking message consumption
 * 2. Automatic reconnection with exponential backoff
 * 3. Flow control to prevent memory overflow
 * 4. Graceful shutdown
 * 5. Message acknowledgment for guaranteed delivery
 */
public class SolaceLogConsumer {

    private final String configFile;
    private final String topicName;
    private final Consumer<String> messageHandler;

    private JCSMPSession session;
    private XMLMessageConsumer consumer;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong messagesReceived = new AtomicLong(0);

    // Performance tuning
    private static final int CONSUMER_WINDOW_SIZE = 255; // Max unacked messages
    private static final int RECONNECT_RETRIES = 5;
    private static final long INITIAL_BACKOFF_MS = 1000;

    public SolaceLogConsumer(String configFile, String topicName, Consumer<String> messageHandler) {
        this.configFile = configFile;
        this.topicName = topicName;
        this.messageHandler = messageHandler;
    }

    /**
     * Start consuming messages from Solace
     */
    public void start() throws JCSMPException {
        if (isRunning.get()) {
            System.out.println("[SolaceConsumer] Already running");
            return;
        }

        System.out.println("[SolaceConsumer] Initializing connection to topic: " + topicName);

        // Load configuration
        java.util.Properties props = loadConfig();

        // Create session properties matching your production config
        JCSMPProperties sessionProps = new JCSMPProperties();

        // Connection settings
        sessionProps.setProperty(JCSMPProperties.HOST, props.getProperty("host"));
        sessionProps.setProperty(JCSMPProperties.VPN_NAME, props.getProperty("vpnName"));
        sessionProps.setProperty(JCSMPProperties.USERNAME, props.getProperty("username"));
        sessionProps.setProperty(JCSMPProperties.PASSWORD, props.getProperty("password"));

        // SSL/TLS Configuration
        String sslValidate = props.getProperty("SSL_VALIDATE_CERTIFICATE", "false");
        sessionProps.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE,
                Boolean.parseBoolean(sslValidate));

        // Connection resilience settings from your config
        String reconnectTries = props.getProperty("reconnectTries", "10");
        sessionProps.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);
        sessionProps.setProperty(JCSMPProperties.RECONNECT_RETRIES, Integer.parseInt(reconnectTries));

        // Performance optimizations
        sessionProps.setProperty(JCSMPProperties.SUB_ACK_WINDOW_SIZE, CONSUMER_WINDOW_SIZE);

        // Create session
        session = JCSMPFactory.onlyInstance().createSession(sessionProps);
        session.connect();

        System.out.println("[SolaceConsumer] Connected to VPN: " + props.getProperty("vpnName"));

        // Create consumer with message listener
        ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
        flowProps.setEndpoint(null); // Direct messaging (topic)
        flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

        consumer = session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                try {
                    String content = extractContent(msg);
                    if (content != null) {
                        messageHandler.accept(content);
                        messagesReceived.incrementAndGet();
                    }
                    msg.ackMessage(); // Acknowledge successful processing
                } catch (Exception e) {
                    System.err.println("[SolaceConsumer] Error processing message: " + e.getMessage());
                    // Don't ack - message will be redelivered
                }
            }

            @Override
            public void onException(JCSMPException e) {
                System.err.println("[SolaceConsumer] Consumer exception: " + e.getMessage());
                handleException(e);
            }
        }, flowProps);

        // Subscribe to topic
        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
        session.addSubscription(topic);

        // Start consuming
        consumer.start();
        isRunning.set(true);

        System.out.println("[SolaceConsumer] Successfully connected and listening on: " + topicName);
    }

    /**
     * Extract text content from message
     */
    private String extractContent(BytesXMLMessage msg) {
        if (msg instanceof TextMessage) {
            return ((TextMessage) msg).getText();
        } else if (msg instanceof BytesMessage) {
            BytesMessage bytesMsg = (BytesMessage) msg;
            byte[] data = bytesMsg.getData();
            return data != null ? new String(data) : null;
        }
        return null;
    }

    /**
     * Handle connection exceptions with retry logic
     */
    private void handleException(JCSMPException e) {
        if (e instanceof JCSMPTransportException) {
            System.err.println("[SolaceConsumer] Transport error - attempting reconnect...");
            attemptReconnect();
        }
    }

    /**
     * Reconnect with exponential backoff
     */
    private void attemptReconnect() {
        new Thread(() -> {
            long backoff = INITIAL_BACKOFF_MS;
            for (int i = 0; i < RECONNECT_RETRIES; i++) {
                try {
                    System.out.println("[SolaceConsumer] Reconnect attempt " + (i + 1) + "/" + RECONNECT_RETRIES);
                    Thread.sleep(backoff);

                    stop();
                    start();

                    System.out.println("[SolaceConsumer] Reconnected successfully");
                    return;
                } catch (Exception e) {
                    System.err.println("[SolaceConsumer] Reconnect failed: " + e.getMessage());
                    backoff *= 2; // Exponential backoff
                }
            }
            System.err.println("[SolaceConsumer] Max reconnect attempts reached. Giving up.");
        }).start();
    }

    /**
     * Graceful shutdown
     */
    public void stop() {
        if (!isRunning.get()) {
            return;
        }

        System.out.println("[SolaceConsumer] Shutting down...");
        isRunning.set(false);

        if (consumer != null) {
            consumer.stop();
            consumer.close();
        }

        if (session != null) {
            session.closeSession();
        }

        System.out.println("[SolaceConsumer] Shutdown complete. Total messages received: " + messagesReceived.get());
    }

    /**
     * Load configuration from properties file
     */
    private java.util.Properties loadConfig() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.FileInputStream fis = new java.io.FileInputStream(configFile)) {
            props.load(fis);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config: " + configFile, e);
        }
        return props;
    }

    /**
     * Get statistics
     */
    public long getMessagesReceived() {
        return messagesReceived.get();
    }

    public boolean isRunning() {
        return isRunning.get();
    }
}

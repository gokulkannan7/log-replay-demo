package com.logreplay.solace;

import com.solacesystems.jcsmp.*;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Clean Solace Consumer Test
 * Connects to Solace and prints all received messages
 */
public class SimpleSolaceTest {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   Solace Consumer Connection Test     ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        JCSMPSession session = null;
        XMLMessageConsumer consumer = null;

        try {
            // ========================================
            // STEP 1: Load Configuration
            // ========================================
            Properties config = new Properties();
            config.load(new FileInputStream("solace.properties"));

            String host = config.getProperty("host");
            String vpn = config.getProperty("vpnName");
            String username = config.getProperty("username");
            String password = config.getProperty("password");
            String topicName = config.getProperty("topic_oms");

            System.out.println("📋 Configuration:");
            System.out.println("   Host: " + host);
            System.out.println("   VPN: " + vpn);
            System.out.println("   Username: " + username);
            System.out.println("   Topic: " + topicName);
            System.out.println();

            // ========================================
            // STEP 2: Create Session Properties
            // ========================================
            JCSMPProperties sessionProps = new JCSMPProperties();
            sessionProps.setProperty(JCSMPProperties.HOST, host);
            sessionProps.setProperty(JCSMPProperties.VPN_NAME, vpn);
            sessionProps.setProperty(JCSMPProperties.USERNAME, username);
            sessionProps.setProperty(JCSMPProperties.PASSWORD, password);

            // SSL Settings
            sessionProps.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);
            sessionProps.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE_DATE, false);

            // Connection Settings
            sessionProps.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);
            sessionProps.setProperty(JCSMPProperties.GENERATE_SEQUENCE_NUMBERS, false);

            // ========================================
            // STEP 3: Connect to Solace
            // ========================================
            System.out.println("🔌 Connecting to Solace broker...");
            session = JCSMPFactory.onlyInstance().createSession(sessionProps);
            session.connect();
            System.out.println("✅ Connected successfully!\n");

            // ========================================
            // STEP 4: Create Message Listener
            // ========================================
            final CountDownLatch messageReceivedLatch = new CountDownLatch(1);

            XMLMessageListener messageListener = new XMLMessageListener() {
                private int messageCount = 0;

                @Override
                public void onReceive(BytesXMLMessage msg) {
                    messageCount++;
                    messageReceivedLatch.countDown(); // Signal that we received at least one message

                    System.out.println("\n" + "=".repeat(80));
                    System.out.println("📨 MESSAGE #" + messageCount + " RECEIVED");
                    System.out.println("=".repeat(80));

                    try {
                        // Extract message content
                        String content = extractMessageContent(msg);

                        // Print message details
                        System.out.println("Destination: " + msg.getDestination());
                        System.out.println("Message ID: " + msg.getApplicationMessageId());
                        System.out.println("Timestamp: " + msg.getSenderTimestamp());
                        System.out.println("Delivery Mode: " + msg.getDeliveryMode());
                        System.out.println("\nContent:");
                        System.out.println(content);
                        System.out.println("=".repeat(80) + "\n");

                        // Acknowledge the message
                        msg.ackMessage();

                    } catch (Exception e) {
                        System.err.println("❌ Error processing message: " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public void onException(JCSMPException e) {
                    System.err.println("❌ Consumer Exception: " + e.getMessage());
                    e.printStackTrace();
                }
            };

            // ========================================
            // STEP 5: Create Consumer
            // ========================================
            System.out.println("🎧 Creating message consumer...");

            ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
            flowProps.setEndpoint(null); // Use direct messaging (topic-based)
            flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

            consumer = session.getMessageConsumer(messageListener, flowProps);
            consumer.start();

            System.out.println("✅ Consumer started\n");

            // ========================================
            // STEP 6: Subscribe to Topic
            // ========================================
            System.out.println("📡 Subscribing to topic: " + topicName);
            Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
            session.addSubscription(topic);

            System.out.println("✅ Subscription active!\n");
            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║  Listening for messages...             ║");
            System.out.println("║  Press Ctrl+C to stop                  ║");
            System.out.println("╚════════════════════════════════════════╝\n");

            // ========================================
            // STEP 7: Keep Running
            // ========================================
            final CountDownLatch shutdownLatch = new CountDownLatch(1);
            final JCSMPSession finalSession = session;
            final XMLMessageConsumer finalConsumer = consumer;

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n\n🛑 Shutdown signal received...");
                shutdownLatch.countDown();
            }));

            // Wait for shutdown
            shutdownLatch.await();

        } catch (Exception e) {
            System.err.println("\n❌ ERROR: " + e.getMessage());
            e.printStackTrace();

        } finally {
            // ========================================
            // CLEANUP
            // ========================================
            System.out.println("\n🧹 Cleaning up resources...");

            if (consumer != null) {
                consumer.stop();
                consumer.close();
                System.out.println("   ✓ Consumer closed");
            }

            if (session != null) {
                session.closeSession();
                System.out.println("   ✓ Session closed");
            }

            System.out.println("\n👋 Goodbye!\n");
        }
    }

    /**
     * Extract message content from different message types
     */
    private static String extractMessageContent(BytesXMLMessage msg) {
        if (msg instanceof TextMessage) {
            return ((TextMessage) msg).getText();
        } else if (msg instanceof BytesMessage) {
            byte[] data = ((BytesMessage) msg).getData();
            return data != null ? new String(data) : "[Empty]";
        } else {
            return msg.dump();
        }
    }
}

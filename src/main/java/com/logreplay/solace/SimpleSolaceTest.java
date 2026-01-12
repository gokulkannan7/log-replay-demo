package com.logreplay.solace;

import com.solacesystems.jcsmp.*;

import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Simple Solace Connection Test
 * 
 * This class ONLY:
 * 1. Connects to your Solace VPN
 * 2. Subscribes to the topic
 * 3. Prints every message it receives
 * 
 * No matching, no caching, no UI - just raw message printing.
 */
public class SimpleSolaceTest {

    public static void main(String[] args) {
        System.out.println("=== Solace Connection Test ===\n");

        JCSMPSession session = null;

        try {
            // Load configuration
            Properties config = new Properties();
            config.load(new FileInputStream("solace.properties"));

            System.out.println("Configuration loaded:");
            System.out.println("  Host: " + config.getProperty("host"));
            System.out.println("  VPN: " + config.getProperty("vpnName"));
            System.out.println("  Topic (OMS): " + config.getProperty("topic_oms"));
            System.out.println();

            // Create session properties
            JCSMPProperties sessionProps = new JCSMPProperties();
            sessionProps.setProperty(JCSMPProperties.HOST, config.getProperty("host"));
            sessionProps.setProperty(JCSMPProperties.VPN_NAME, config.getProperty("vpnName"));
            sessionProps.setProperty(JCSMPProperties.USERNAME, config.getProperty("username"));
            sessionProps.setProperty(JCSMPProperties.PASSWORD, config.getProperty("password"));

            // SSL Configuration
            sessionProps.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);

            // Connection settings
            sessionProps.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);

            System.out.println("Connecting to Solace...");

            // Create and connect session
            session = JCSMPFactory.onlyInstance().createSession(sessionProps);
            session.connect();

            System.out.println("✓ Connected successfully!\n");

            // Create message consumer with simple print listener
            final JCSMPSession finalSession = session;
            XMLMessageConsumer consumer = session.getMessageConsumer(new XMLMessageListener() {
                @Override
                public void onReceive(BytesXMLMessage msg) {
                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    System.out.println("MESSAGE RECEIVED:");
                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

                    // Print message type
                    if (msg instanceof TextMessage) {
                        TextMessage textMsg = (TextMessage) msg;
                        System.out.println("Type: TextMessage");
                        System.out.println("Content:\n" + textMsg.getText());
                    } else if (msg instanceof BytesMessage) {
                        BytesMessage bytesMsg = (BytesMessage) msg;
                        byte[] data = bytesMsg.getData();
                        System.out.println("Type: BytesMessage");
                        System.out.println("Content:\n" + (data != null ? new String(data) : "null"));
                    } else {
                        System.out.println("Type: " + msg.getClass().getSimpleName());
                        System.out.println("Content: " + msg.dump());
                    }

                    // Print metadata
                    System.out.println("\nMetadata:");
                    System.out.println("  Destination: " + msg.getDestination());
                    System.out.println("  Timestamp: " + msg.getSenderTimestamp());
                    System.out.println("  Message ID: " + msg.getApplicationMessageId());

                    System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

                    // Acknowledge the message
                    msg.ackMessage();
                }

                @Override
                public void onException(JCSMPException e) {
                    System.err.println("❌ Consumer exception: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            // Subscribe to the OMS topic
            String topicName = config.getProperty("topic_oms");
            Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);

            System.out.println("Subscribing to topic: " + topicName);
            session.addSubscription(topic);

            // Start consuming
            consumer.start();

            System.out.println("✓ Subscription active!");
            System.out.println("\n🎧 Listening for messages... (Press Ctrl+C to stop)\n");

            // Keep the program running
            CountDownLatch latch = new CountDownLatch(1);

            // Shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n\nShutting down...");
                latch.countDown();
            }));

            // Wait indefinitely
            latch.await();

        } catch (Exception e) {
            System.err.println("\n❌ Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            if (session != null) {
                session.closeSession();
                System.out.println("Session closed.");
            }
        }
    }
}

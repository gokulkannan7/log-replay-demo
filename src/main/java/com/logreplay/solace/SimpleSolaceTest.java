package com.logreplay.solace;

// import com.logreplay.validate.source.CryptUtil; // TODO: Add your CryptUtil class
import com.solacesystems.jcsmp.*;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Solace Consumer Test - Minimal Logging Version
 */
public class SimpleSolaceTest {

    public static void main(String[] args) {
        System.out.println("=== Solace Connection Test ===\n");

        JCSMPSession session = null;
        XMLMessageConsumer consumer = null;

        try {
            // Load and decrypt configuration
            Properties config = new Properties();
            config.load(new FileInputStream("solace.properties"));

            String host = config.getProperty("host");
            String vpn = config.getProperty("vpnName");
            // TODO: Decrypt credentials using your CryptUtil
            String username = config.getProperty("username");
            String password = config.getProperty("password");
            String topicName = config.getProperty("topic_oms");

            System.out.println("Connecting to: " + host);
            System.out.println("Topic: " + topicName + "\n");

            // Create session
            JCSMPProperties sessionProps = new JCSMPProperties();
            sessionProps.setProperty(JCSMPProperties.HOST, host);
            sessionProps.setProperty(JCSMPProperties.VPN_NAME, vpn);
            sessionProps.setProperty(JCSMPProperties.USERNAME, username);
            sessionProps.setProperty(JCSMPProperties.PASSWORD, password);
            sessionProps.setBooleanProperty(JCSMPProperties.SSL_VALIDATE_CERTIFICATE, false);
            sessionProps.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);

            session = JCSMPFactory.onlyInstance().createSession(sessionProps);
            session.connect();
            System.out.println("✓ Connected\n");

            // Create consumer
            consumer = session.getMessageConsumer(new XMLMessageListener() {
                private int count = 0;

                @Override
                public void onReceive(BytesXMLMessage msg) {
                    count++;
                    String content = (msg instanceof TextMessage)
                            ? ((TextMessage) msg).getText()
                            : new String(((BytesMessage) msg).getData());

                    System.out.println("Message #" + count + ":");
                    System.out.println(content);
                    System.out.println();
                    msg.ackMessage();
                }

                @Override
                public void onException(JCSMPException e) {
                    System.err.println("Error: " + e.getMessage());
                }
            });

            consumer.start();

            // Subscribe
            Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
            session.addSubscription(topic);

            System.out.println("✓ Listening... (Ctrl+C to stop)\n");

            // Wait
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            latch.await();

        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (consumer != null)
                consumer.close();
            if (session != null)
                session.closeSession();
            System.out.println("\nDisconnected.");
        }
    }
}

package com.logreplay.source;

import com.solacesystems.jcsmp.*;
import java.io.FileInputStream;
import java.util.Properties;
import java.util.function.Consumer;

public class SolaceReceiver {

    private JCSMPSession session;
    private final String topicName;
    private final Consumer<String> onMessageReceived;

    // MOCK MODE FLAG: Set to 'false' in Production
    private static final boolean MOCK_MODE = true;

    public SolaceReceiver(String configFile, String topicKey, Consumer<String> onMessageReceived) {
        this.onMessageReceived = onMessageReceived;

        Properties props = new Properties();
        try {
            props.load(new FileInputStream(configFile));
            this.topicName = props.getProperty(topicKey);

            if (!MOCK_MODE) {
                initSolace(props);
            } else {
                System.out.println("[SolaceReceiver] MOCK MODE ACTIVE. Simulating stream for " + topicName);
                startMockStream();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing SolaceReceiver", e);
        }
    }

    private void initSolace(Properties props) throws JCSMPException {
        System.out.println("[SolaceReceiver] Connecting to VPN: " + props.getProperty("vpn"));
        JCSMPProperties solaceProps = new JCSMPProperties();
        solaceProps.setProperty(JCSMPProperties.HOST, props.getProperty("host"));
        solaceProps.setProperty(JCSMPProperties.VPN_NAME, props.getProperty("vpn"));
        solaceProps.setProperty(JCSMPProperties.USERNAME, props.getProperty("username"));
        solaceProps.setProperty(JCSMPProperties.PASSWORD, props.getProperty("password"));

        session = JCSMPFactory.onlyInstance().createSession(solaceProps);

        session.getMessageConsumer(new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage msg) {
                if (msg instanceof TextMessage) {
                    String content = ((TextMessage) msg).getText();
                    onMessageReceived.accept(content);
                }
                msg.ackMessage();
            }

            @Override
            public void onException(JCSMPException e) {
                System.err.println("Consumer Exception: " + e.getMessage());
            }
        }).start();

        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicName);
        session.addSubscription(topic);
        session.connect();
        System.out.println("[SolaceReceiver] Connected. Listening on: " + topicName);
    }

    // Simulates incoming messages from a file for testing without a real broker
    private void startMockStream() {
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait for system to warm up

                // Simulate a Replayed Message
                // We use the content from the previous replayed.log for demo
                String[] mockMessages = {
                        "2025-12-05T06:00:00.600 client INFO parse: 8=FIX.4.2^A35=D^A49=BLPCGAP^A56=CGA6^A37=ORDER_001^A11=5DTX000802XB001^A55=KEY_MISMATCH^A",
                        "2025-12-05T06:00:00.530 client INFO parse: 8=FIX.4.2^A35=D^A37=ORDER_002^A11=5DTX00070WZ001^A55=VOD^A"
                };

                for (String msg : mockMessages) {
                    onMessageReceived.accept(msg);
                    Thread.sleep(1000); // Simulate network delay
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}

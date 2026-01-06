package com.logreplay.simple;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.io.File;

/**
 * A basic Aeron Subscriber that launches its own embedded Media Driver.
 * This represents the second side of the "exchange between two media drivers".
 */
public class SimpleSubscriber {
    public static void main(String[] args) {
        String channel = "aeron:udp?endpoint=localhost:40123";
        int streamId = 10;

        // Define a unique directory for this driver so it doesn't conflict with
        // Publisher's driver
        String driverDir = System.getProperty("user.home") + "/aeron-simple-subscriber";

        System.out.println("Starting Subscriber Media Driver at: " + driverDir);

        // Launch a dedicated Media Driver for this subscriber
        // This is Driver #2
        try (MediaDriver driver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .aeronDirectoryName(driverDir)
                .dirDeleteOnStart(true));
                Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driverDir))) {

            System.out.println("Subscriber connected to Driver.");

            Subscription subscription = aeron.addSubscription(channel, streamId);
            System.out.println("Subscribed to " + channel + " on stream " + streamId);

            FragmentHandler handler = (buffer, offset, length, header) -> {
                byte[] data = new byte[length];
                buffer.getBytes(offset, data);
                System.out.println("Received: " + new String(data));
            };

            SleepingMillisIdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1);

            while (true) {
                int fragments = subscription.poll(handler, 10);
                idleStrategy.idle(fragments);
            }
        }
    }
}

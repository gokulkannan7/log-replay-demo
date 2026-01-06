package com.logreplay.simple;

import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.driver.MediaDriver;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A basic Aeron Publisher that launches its own embedded Media Driver.
 * This represents one side of the "exchange between two media drivers".
 */
public class SimplePublisher {
    public static void main(String[] args) {
        String channel = "aeron:udp?endpoint=localhost:40123";
        int streamId = 10;

        // Define a unique directory for this driver so it doesn't conflict with others
        String driverDir = System.getProperty("user.home") + "/aeron-simple-publisher";

        System.out.println("Starting Publisher Media Driver at: " + driverDir);

        // Launch a dedicated Media Driver for this publisher
        // This is Driver #1 in the "two media drivers" requirement
        try (MediaDriver driver = MediaDriver.launchEmbedded(new MediaDriver.Context()
                .aeronDirectoryName(driverDir)
                .dirDeleteOnStart(true));
                Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driverDir))) {

            System.out.println("Publisher connected to Driver.");

            // Add Publication
            try (Publication publication = aeron.addPublication(channel, streamId)) {
                System.out.println("Publishing to " + channel + " on stream " + streamId);

                UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocate(256));
                long messageCount = 0;

                while (true) {
                    // Create message
                    String message = "Hello from Publisher! Count: " + ++messageCount;
                    byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                    buffer.putBytes(0, messageBytes);

                    // Offer message
                    long result = publication.offer(buffer, 0, messageBytes.length);

                    if (result > 0) {
                        System.out.println("Sent: " + message);
                    } else if (result == Publication.BACK_PRESSURED) {
                        System.out.println("Back pressure...");
                    } else if (result == Publication.NOT_CONNECTED) {
                        System.out.println("No subscribers connected...");
                    } else {
                        System.out.println("Offer failed: " + result);
                    }

                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

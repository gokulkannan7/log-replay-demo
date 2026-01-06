package com.logreplay.gateway;

import com.logreplay.matching.MatchingService;
import com.google.gson.Gson;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.io.IOException;

public class UIRestGateway extends WebSocketServer {

    private final Gson gson = new Gson();

    public UIRestGateway(InetSocketAddress address) {
        super(address);
    }

    public static void main(String[] args) throws IOException {
        int port = 8080;
        UIRestGateway server = new UIRestGateway(new InetSocketAddress(port));
        server.start();
        System.out.println("WebSocket Gateway Server started on port: " + port);

        // In a real app, we might trigger this on connection or on a specific "START"
        // command.
        // For this demo, we wait for a client to connect, then run the matching
        // service.
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection from " + conn.getRemoteSocketAddress());

        // When a specific client connects, we start streaming the log replay to them.
        // We run this in a separate thread so we don't block the WebSocket selector
        // thread.
        new Thread(() -> {
            try {
                System.out.println("Starting Log Replay Stream for client...");
                MatchingService.streamComparison("logs/original.log", "logs/replayed.log", result -> {
                    // Convert result to JSON and send
                    String json = gson.toJson(result);
                    conn.send(json);

                    // Simulate some processing delay so the UI looks cool streaming in
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                    }
                });

                // Send a completion message
                conn.send("{\"status\": \"COMPLETE\"}");
                System.out.println("Stream complete.");
            } catch (Exception e) {
                e.printStackTrace();
                conn.close();
            }
        }).start();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Received message: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server started successfully");
    }
}

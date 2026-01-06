package com.logreplay.gateway;

import com.logreplay.matching.MatchingService;
import com.google.gson.Gson;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;

public class UIRestGateway extends WebSocketServer {

    private final Gson gson = new Gson();

    public UIRestGateway(InetSocketAddress address) {
        super(address);
    }

    public static void main(String[] args) {
        int port = 8888;
        UIRestGateway server = new UIRestGateway(new InetSocketAddress(port));
        server.start();
        System.out.println("WebSocket Gateway Server started on port: " + port);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("New connection from " + conn.getRemoteSocketAddress());

        new Thread(() -> {
            try {
                System.out.println("Starting Log Replay Stream...");
                MatchingService.streamComparison("logs/original.log", "logs/replayed.log", result -> {
                    String json = gson.toJson(result);
                    conn.send(json);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
                });

                conn.send("{\"status\": \"COMPLETE\"}");
                System.out.println("Stream complete.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Closed connection: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
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

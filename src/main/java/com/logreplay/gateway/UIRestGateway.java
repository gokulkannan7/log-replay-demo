package com.logreplay.gateway;

import com.logreplay.solace.SolaceReplayEngine;
import com.logreplay.solace.SolaceReplayEngine.ComparisonResult;
import com.google.gson.Gson;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * WebSocket Gateway for Real-Time Log Replay Results
 * Connects Solace replay engine to React dashboard
 */
public class UIRestGateway extends WebSocketServer {

    private final Gson gson = new Gson();
    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());
    private SolaceReplayEngine engine;

    public UIRestGateway(InetSocketAddress address) {
        super(address);
    }

    public static void main(String[] args) {
        int port = 8888;
        UIRestGateway server = new UIRestGateway(new InetSocketAddress(port));

        try {
            // Start WebSocket server
            server.start();
            System.out.println("[Gateway] WebSocket server started on port: " + port);

            // Initialize replay engine (Single Log Config)
            System.out.println("[Gateway] Initializing replay engine...\n");

            server.engine = new SolaceReplayEngine(
                    "logs/OneOmsFixSrcOriginal.log", // Just one log file
                    result -> server.broadcastResult(result));

            // Start consuming from Solace
            server.engine.start("solace.properties");

            System.out.println("[Gateway] Engine started - streaming results to UI\n");

            // Shutdown hook
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[Gateway] Shutdown initiated...");
                if (server.engine != null) {
                    server.engine.shutdown();
                }
                try {
                    server.stop(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }));

        } catch (Exception e) {
            System.err.println("[Gateway] ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private void broadcastResult(ComparisonResult result) {
        String json = gson.toJson(result);
        synchronized (clients) {
            for (WebSocket client : clients) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clients.add(conn);
        System.out.println("[Gateway] Client connected: " + conn.getRemoteSocketAddress());

        if (engine != null) {
            String stats = String.format(
                    "{\"type\":\"stats\",\"processed\":%d,\"mismatches\":%d,\"remaining\":%d}",
                    engine.getProcessedCount(),
                    engine.getMismatchCount(),
                    engine.getRemaining());
            conn.send(stats);
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clients.remove(conn);
        System.out.println("[Gateway] Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[Gateway] WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[Gateway] WebSocket server ready");
    }
}

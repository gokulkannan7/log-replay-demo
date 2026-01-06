package com.logreplay.gateway;

import com.logreplay.matching.MatchingService;
import com.google.gson.Gson;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class UIRestGateway extends WebSocketServer {

    private final Gson gson = new Gson();
    private final Set<WebSocket> clients = Collections.synchronizedSet(new HashSet<>());

    public UIRestGateway(InetSocketAddress address) {
        super(address);
    }

    public static void main(String[] args) {
        int port = 8080;
        UIRestGateway server = new UIRestGateway(new InetSocketAddress(port));
        server.start();
        System.out.println("WebSocket Gateway Server started on port: " + port);

        // Start the Verification Engine immediately.
        // It will listen to Solace and push checks to any connected clients.
        MatchingService.startEngine(result -> {
            server.broadcastResult(result);
        });
    }

    public void broadcastResult(MatchingService.ComparisonResult result) {
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
        System.out.println("New Dashboard Connected: " + conn.getRemoteSocketAddress());
        clients.add(conn);
        // We could send a "Welcome" or "Status" packet here
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("Dashboard Disconnected: " + conn.getRemoteSocketAddress());
        clients.remove(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Cmd received: " + message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("Server socket bound.");
    }
}

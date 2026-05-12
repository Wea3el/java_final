package com.expensesplitter.server;

import com.expensesplitter.currency.CurrencyService;
import com.expensesplitter.db.DatabaseManager;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    public static final int PORT = 9090;

    private final DatabaseManager db;
    private final CurrencyService currencyService;
    private final Map<Integer, Set<ClientHandler>> groupClients = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public Server() throws SQLException {
        this.db = new DatabaseManager();
        this.currencyService = new CurrencyService();
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("ExpenseSplitter server started on port " + PORT);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                db.close();
                threadPool.shutdown();
                System.out.println("Server shut down.");
            }));
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                ClientHandler handler = new ClientHandler(clientSocket, db, currencyService, this);
                threadPool.submit(handler);
            }
        }
    }

    public void registerClientInGroup(int groupId, ClientHandler handler) {
        groupClients.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(handler);
    }

    public void unregisterClientFromGroup(int groupId, ClientHandler handler) {
        Set<ClientHandler> clients = groupClients.get(groupId);
        if (clients != null) clients.remove(handler);
    }

    /** Broadcast a message to all group members except the sender. */
    public void broadcastToGroup(int groupId, String message, ClientHandler sender) {
        Set<ClientHandler> clients = groupClients.get(groupId);
        if (clients == null) return;
        for (ClientHandler client : clients)
            if (client != sender) client.sendMessage(message);
    }

    public static void main(String[] args) {
        try {
            new Server().start();
        } catch (Exception e) {
            System.err.println("Server failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package com.expensesplitter.client;

import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Client {
    private static final String HOST = "localhost";
    private static final int PORT = 9090;

    private Socket socket;
    private PrintWriter writer;
    private final List<Consumer<JSONObject>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean connected = false;

    public void connect() throws IOException {
        socket = new Socket(HOST, PORT);
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        connected = true;
        Thread t = new Thread(this::readLoop, "ClientReader");
        t.setDaemon(true);
        t.start();
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                JSONObject msg = new JSONObject(line);
                for (Consumer<JSONObject> listener : listeners)
                    listener.accept(msg);
            }
        } catch (IOException e) {
            if (connected) System.err.println("Server connection lost.");
        } finally {
            connected = false;
        }
    }

    public void send(String type, JSONObject data) {
        if (!connected) throw new IllegalStateException("Not connected");
        JSONObject msg = new JSONObject().put("type", type).put("data", data != null ? data : new JSONObject());
        writer.println(msg.toString());
    }

    public void addListener(Consumer<JSONObject> listener) { listeners.add(listener); }
    public void removeListener(Consumer<JSONObject> listener) { listeners.remove(listener); }
    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }
}

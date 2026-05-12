package com.expensesplitter.server;

import com.expensesplitter.algorithm.DebtSettler;
import com.expensesplitter.currency.CurrencyService;
import com.expensesplitter.db.DatabaseManager;
import com.expensesplitter.model.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final DatabaseManager db;
    private final CurrencyService currencyService;
    private final Server server;
    private PrintWriter writer;
    private User currentUser;
    private int activeGroupId = -1;

    public ClientHandler(Socket socket, DatabaseManager db, CurrencyService currencyService, Server server) {
        this.socket = socket;
        this.db = db;
        this.currencyService = currencyService;
        this.server = server;
    }

    @Override
    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {
            this.writer = pw;
            String line;
            while ((line = reader.readLine()) != null) {
                try { handleMessage(new JSONObject(line)); }
                catch (Exception e) { sendError("Error: " + e.getMessage()); }
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " +
                (currentUser != null ? currentUser.getUsername() : socket.getRemoteSocketAddress()));
        } finally {
            if (activeGroupId != -1) server.unregisterClientFromGroup(activeGroupId, this);
        }
    }

    private void handleMessage(JSONObject msg) throws SQLException {
        String type = msg.getString("type");
        JSONObject data = msg.optJSONObject("data");
        if (data == null) data = new JSONObject();

        switch (type) {
            case "LOGIN"                 -> handleLogin(data);
            case "REGISTER"              -> handleRegister(data);
            case "CREATE_GROUP"          -> handleCreateGroup(data);
            case "JOIN_GROUP"            -> handleJoinGroup(data);
            case "LIST_GROUPS"           -> handleListGroups();
            case "GET_GROUP_MEMBERS"     -> handleGetParticipants(data);
            case "ADD_MEMBER"            -> handleAddParticipant(data);
            case "REMOVE_MEMBER"         -> handleRemoveParticipant(data);
            case "ADD_EXPENSE"           -> handleAddExpense(data);
            case "LIST_EXPENSES"         -> handleListExpenses(data);
            case "LIST_PAID_EXPENSES"    -> handleListPaidExpenses(data);
            case "GET_BALANCES"          -> handleGetBalances(data);
            case "GET_SETTLEMENTS"       -> handleGetSettlements(data);
            case "MARK_PAID"             -> handleMarkPaid(data);
            case "GET_RATES"             -> handleGetRates(data);
            case "GET_CATEGORY_SPENDING" -> handleGetCategorySpending(data);
            case "GET_USER_SPENDING"     -> handleGetParticipantSpending(data);
            case "SET_ACTIVE_GROUP"      -> handleSetActiveGroup(data);
            case "SETTLE_ALL"            -> handleSettleAll(data);
            case "CLEAR_ALL"             -> handleClearAll(data);
            case "GET_PAIRWISE_DEBTS"    -> handleGetPairwiseDebts(data);
            case "SETTLE_PAIR"           -> handleSettlePair(data);
            default                      -> sendError("Unknown message type: " + type);
        }
    }

    private void handleLogin(JSONObject data) throws SQLException {
        User user = db.authenticateUser(data.getString("username"), data.getString("password"));
        JSONObject r = response("LOGIN");
        if (user != null) {
            currentUser = user;
            r.put("success", true).put("userId", user.getId()).put("username", user.getUsername());
        } else {
            r.put("success", false).put("message", "Invalid username or password");
        }
        send(r);
    }

    private void handleRegister(JSONObject data) throws SQLException {
        JSONObject r = response("REGISTER");
        try {
            User user = db.createUser(data.getString("username"), data.getString("password"));
            if (user != null) {
                currentUser = user;
                r.put("success", true).put("userId", user.getId()).put("username", user.getUsername());
            } else {
                r.put("success", false).put("message", "Registration failed");
            }
        } catch (SQLException e) {
            r.put("success", false).put("message",
                e.getMessage().contains("UNIQUE") ? "Username already taken" : e.getMessage());
        }
        send(r);
    }

    private void handleCreateGroup(JSONObject data) throws SQLException {
        requireAuth();
        Group group = db.createGroup(data.getString("name"), currentUser.getId());
        send(response("CREATE_GROUP").put("success", true)
            .put("groupId", group.getId()).put("name", group.getName()));
    }

    private void handleJoinGroup(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        Group group = db.getGroupById(groupId);
        JSONObject r = response("JOIN_GROUP");
        if (group == null) {
            r.put("success", false).put("message", "Group not found");
        } else {
            db.joinGroup(groupId, currentUser.getId());
            r.put("success", true).put("groupId", groupId).put("name", group.getName());
        }
        send(r);
    }

    private void handleListGroups() throws SQLException {
        requireAuth();
        JSONArray arr = new JSONArray();
        for (Group g : db.getUserGroups(currentUser.getId()))
            arr.put(new JSONObject().put("id", g.getId()).put("name", g.getName()));
        send(response("LIST_GROUPS").put("success", true).put("groups", arr));
    }

    // Returns participants (names only — no user account needed)
    private void handleGetParticipants(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        JSONArray arr = new JSONArray();
        for (Participant p : db.getParticipants(groupId))
            arr.put(new JSONObject().put("id", p.getId()).put("username", p.getName()));
        send(response("GET_GROUP_MEMBERS").put("success", true)
            .put("groupId", groupId).put("members", arr));
    }

    private void handleAddParticipant(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        String name  = data.getString("username").trim();
        JSONObject r = response("ADD_MEMBER");
        if (name.isEmpty()) {
            r.put("success", false).put("message", "Name cannot be empty");
        } else {
            Participant p = db.addParticipant(groupId, name);
            if (p != null) {
                r.put("success", true).put("username", name);
                server.broadcastToGroup(groupId,
                    new JSONObject().put("type", "BROADCAST").put("action", "MEMBER_ADDED")
                        .put("groupId", groupId).put("username", name).toString(), this);
            } else {
                r.put("success", false).put("message", "Could not add participant");
            }
        }
        send(r);
    }

    private void handleRemoveParticipant(JSONObject data) throws SQLException {
        requireAuth();
        int groupId       = data.getInt("groupId");
        int participantId = data.getInt("userId");
        db.removeParticipant(groupId, participantId);
        send(response("REMOVE_MEMBER").put("success", true));
        server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "MEMBER_REMOVED")
                .put("groupId", groupId).put("userId", participantId).toString(), this);
    }

    private void handleAddExpense(JSONObject data) throws SQLException {
        requireAuth();
        int groupId      = data.getInt("groupId");
        String description = data.getString("description");
        double totalAmount = data.getDouble("totalAmount");
        String currency  = data.optString("currency", "USD");
        String category  = data.optString("category", "General");
        int paidById     = data.optInt("paidBy", -1);
        double amountUSD = currencyService.convert(totalAmount, currency, "USD");

        JSONArray splitsArr = data.getJSONArray("splits");
        Map<Integer, Double> splits = new LinkedHashMap<>();
        for (int i = 0; i < splitsArr.length(); i++) {
            JSONObject s = splitsArr.getJSONObject(i);
            splits.put(s.getInt("userId"),
                currencyService.convert(s.getDouble("amount"), currency, "USD"));
        }

        Expense expense = db.addExpense(groupId, description, amountUSD, "USD",
            paidById, category, splits);
        send(response("ADD_EXPENSE").put("success", true).put("expenseId", expense.getId()));
        server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "EXPENSE_ADDED")
                .put("groupId", groupId).put("addedBy", currentUser.getUsername())
                .put("description", description).put("amount", amountUSD).toString(), this);
    }

    private void handleListExpenses(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        JSONArray arr = new JSONArray();
        for (Expense e : db.getGroupExpenses(groupId))
            arr.put(new JSONObject()
                .put("id", e.getId())
                .put("description", e.getDescription())
                .put("totalAmount", e.getTotalAmount())
                .put("currency", e.getCurrency())
                .put("paidBy", e.getPaidByUsername())
                .put("paidById", e.getPaidByUserId())
                .put("category", e.getCategory())
                .put("createdAt", e.getCreatedAt()));
        send(response("LIST_EXPENSES").put("success", true).put("expenses", arr));
    }

    private void handleGetBalances(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        Map<String, Double> balances = db.getGroupBalances(groupId);
        JSONArray arr = new JSONArray();
        for (Map.Entry<String, Double> e : balances.entrySet())
            arr.put(new JSONObject()
                .put("username", e.getKey())
                .put("balance", e.getValue()));
        send(response("GET_BALANCES").put("success", true).put("balances", arr));
    }

    private void handleGetSettlements(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        Map<String, Double> balances = db.getGroupBalances(groupId);
        JSONArray arr = new JSONArray();
        for (Settlement s : DebtSettler.computeSettlements(balances))
            arr.put(new JSONObject()
                .put("from", s.getFromUser()).put("to", s.getToUser()).put("amount", s.getAmount()));
        send(response("GET_SETTLEMENTS").put("success", true).put("settlements", arr));
    }

    private void handleListPaidExpenses(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        JSONArray arr = new JSONArray();
        for (Expense e : db.getPaidGroupExpenses(groupId))
            arr.put(new JSONObject()
                .put("id", e.getId())
                .put("description", e.getDescription())
                .put("totalAmount", e.getTotalAmount())
                .put("currency", e.getCurrency())
                .put("paidBy", e.getPaidByUsername())
                .put("category", e.getCategory())
                .put("createdAt", e.getCreatedAt()));
        send(response("LIST_PAID_EXPENSES").put("success", true).put("expenses", arr));
    }

    private void handleMarkPaid(JSONObject data) throws SQLException {
        requireAuth();
        int expenseId     = data.getInt("expenseId");
        int participantId = data.getInt("userId");
        int groupId       = data.getInt("groupId");
        boolean ok = db.markSplitPaid(expenseId, participantId);
        send(response("MARK_PAID").put("success", ok));
        if (ok) server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "PAYMENT_MARKED")
                .put("groupId", groupId).put("markedBy", currentUser.getUsername()).toString(), this);
    }

    private void handleGetRates(JSONObject data) {
        String base = data.optString("base", "USD");
        send(response("GET_RATES").put("success", true).put("base", base)
            .put("rates", new JSONObject(currencyService.getRates(base))));
    }

    private void handleGetCategorySpending(JSONObject data) throws SQLException {
        requireAuth();
        send(response("GET_CATEGORY_SPENDING").put("success", true)
            .put("data", new JSONObject(db.getCategorySpending(data.getInt("groupId")))));
    }

    private void handleGetParticipantSpending(JSONObject data) throws SQLException {
        requireAuth();
        send(response("GET_USER_SPENDING").put("success", true)
            .put("data", new JSONObject(db.getParticipantSpending(data.getInt("groupId")))));
    }

    private void handleGetPairwiseDebts(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        JSONArray arr = new JSONArray();
        for (String[] debt : db.getPairwiseDebts(groupId))
            arr.put(new JSONObject()
                .put("ower",   debt[0])
                .put("owedTo", debt[1])
                .put("amount", Double.parseDouble(debt[2])));
        send(response("GET_PAIRWISE_DEBTS").put("success", true).put("debts", arr));
    }

    private void handleSettlePair(JSONObject data) throws SQLException {
        requireAuth();
        int groupId      = data.getInt("groupId");
        String owerName  = data.getString("ower");
        String owedTo    = data.getString("owedTo");
        db.settlePair(groupId, owerName, owedTo);
        send(response("SETTLE_PAIR").put("success", true));
        server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "PAYMENT_MARKED")
                .put("groupId", groupId).put("markedBy", currentUser.getUsername()).toString(), this);
    }

    private void handleClearAll(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        db.clearAllDebts(groupId);
        send(response("CLEAR_ALL").put("success", true));
        server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "PAYMENT_MARKED")
                .put("groupId", groupId).put("markedBy", currentUser.getUsername()).toString(), this);
    }

    private void handleSettleAll(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        String name = data.getString("participantName");
        int updated = db.settleAll(groupId, name);
        send(response("SETTLE_ALL").put("success", true).put("updated", updated));
        server.broadcastToGroup(groupId,
            new JSONObject().put("type", "BROADCAST").put("action", "PAYMENT_MARKED")
                .put("groupId", groupId).put("markedBy", currentUser.getUsername())
                .put("settledName", name).toString(), this);
    }

    private void handleSetActiveGroup(JSONObject data) throws SQLException {
        requireAuth();
        int groupId = data.getInt("groupId");
        if (activeGroupId != -1) server.unregisterClientFromGroup(activeGroupId, this);
        if (db.isGroupMember(groupId, currentUser.getId())) {
            activeGroupId = groupId;
            server.registerClientInGroup(groupId, this);
            send(response("SET_ACTIVE_GROUP").put("success", true));
        } else {
            send(response("SET_ACTIVE_GROUP").put("success", false).put("message", "Not a group member"));
        }
    }

    private void requireAuth() {
        if (currentUser == null) throw new IllegalStateException("Not authenticated");
    }

    private JSONObject response(String action) {
        return new JSONObject().put("type", "RESPONSE").put("action", action);
    }

    public synchronized void sendMessage(String message) {
        if (writer != null) writer.println(message);
    }

    private void send(JSONObject obj) { sendMessage(obj.toString()); }

    private void sendError(String message) {
        send(new JSONObject().put("type", "ERROR").put("message", message));
    }
}

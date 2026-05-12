package com.expensesplitter.db;

import com.expensesplitter.model.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:expensesplitter.db";
    private final Connection connection;

    public DatabaseManager() throws SQLException {
        connection = DriverManager.getConnection(DB_URL);
        try (Statement s = connection.createStatement()) {
            s.execute("PRAGMA busy_timeout = 5000");
            s.execute("PRAGMA journal_mode=WAL");
        }
        initSchema();
    }

    private synchronized void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Auth tables — always safe
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT UNIQUE NOT NULL,
                    password_hash TEXT NOT NULL
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    created_by INTEGER NOT NULL,
                    FOREIGN KEY(created_by) REFERENCES users(id)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS group_members (
                    group_id INTEGER NOT NULL,
                    user_id INTEGER NOT NULL,
                    PRIMARY KEY(group_id, user_id)
                )
            """);

            // Migrate: if the old expense tables reference users instead of participants,
            // drop them so they get recreated with the new schema below.
            boolean hasParticipants = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='participants'").next();
            if (!hasParticipants) {
                stmt.executeUpdate("DROP TABLE IF EXISTS expense_splits");
                stmt.executeUpdate("DROP TABLE IF EXISTS expenses");
            }

            // Participants — just names, no user account required
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS participants (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    UNIQUE(group_id, name),
                    FOREIGN KEY(group_id) REFERENCES groups(id)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS expenses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    group_id INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    total_amount REAL NOT NULL,
                    currency TEXT NOT NULL DEFAULT 'USD',
                    paid_by_participant INTEGER NOT NULL,
                    category TEXT NOT NULL DEFAULT 'General',
                    created_at TEXT NOT NULL DEFAULT (datetime('now')),
                    FOREIGN KEY(group_id) REFERENCES groups(id),
                    FOREIGN KEY(paid_by_participant) REFERENCES participants(id)
                )
            """);
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS expense_splits (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    expense_id INTEGER NOT NULL,
                    participant_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    is_paid INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(expense_id) REFERENCES expenses(id),
                    FOREIGN KEY(participant_id) REFERENCES participants(id)
                )
            """);
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public synchronized User createUser(String username, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return new User(rs.getInt(1), username);
        }
        return null;
    }

    public synchronized User authenticateUser(String username, String password) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, username FROM users WHERE username = ? AND password_hash = ?")) {
            ps.setString(1, username);
            ps.setString(2, hashPassword(password));
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"));
        }
        return null;
    }

    // ── Groups ────────────────────────────────────────────────────────────────

    public synchronized Group createGroup(String name, int createdByUserId) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int groupId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO groups (name, created_by) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name);
                ps.setInt(2, createdByUserId);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                groupId = rs.next() ? rs.getInt(1) : -1;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)")) {
                ps.setInt(1, groupId);
                ps.setInt(2, createdByUserId);
                ps.executeUpdate();
            }
            connection.commit();
            return new Group(groupId, name, createdByUserId);
        } catch (SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    public synchronized boolean joinGroup(int groupId, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?)")) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized List<Group> getUserGroups(int userId) throws SQLException {
        String sql = """
            SELECT g.id, g.name, g.created_by FROM groups g
            JOIN group_members gm ON g.id = gm.group_id WHERE gm.user_id = ?
        """;
        List<Group> groups = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                groups.add(new Group(rs.getInt("id"), rs.getString("name"), rs.getInt("created_by")));
        }
        return groups;
    }

    public synchronized Group getGroupById(int groupId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, created_by FROM groups WHERE id = ?")) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new Group(rs.getInt("id"), rs.getString("name"), rs.getInt("created_by"));
        }
        return null;
    }

    public synchronized boolean isGroupMember(int groupId, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?")) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeQuery().next();
        }
    }

    // ── Participants (just names, no accounts needed) ─────────────────────────

    public synchronized Participant addParticipant(int groupId, String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO participants (group_id, name) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, groupId);
            ps.setString(2, name.trim());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return new Participant(rs.getInt(1), groupId, name.trim());
        }
        // already exists — fetch existing
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM participants WHERE group_id = ? AND name = ?")) {
            ps.setInt(1, groupId);
            ps.setString(2, name.trim());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new Participant(rs.getInt(1), groupId, name.trim());
        }
        return null;
    }

    public synchronized boolean removeParticipant(int groupId, int participantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM participants WHERE id = ? AND group_id = ?")) {
            ps.setInt(1, participantId);
            ps.setInt(2, groupId);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized List<Participant> getParticipants(int groupId) throws SQLException {
        List<Participant> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name FROM participants WHERE group_id = ? ORDER BY name")) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                list.add(new Participant(rs.getInt("id"), groupId, rs.getString("name")));
        }
        return list;
    }

    // ── Expenses ──────────────────────────────────────────────────────────────

    public synchronized Expense addExpense(int groupId, String description, double totalAmount,
                                           String currency, int paidByParticipantId, String category,
                                           Map<Integer, Double> splits) throws SQLException {
        connection.setAutoCommit(false);
        try {
            int expenseId;
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO expenses (group_id, description, total_amount, currency, paid_by_participant, category) VALUES (?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, groupId);
                ps.setString(2, description);
                ps.setDouble(3, totalAmount);
                ps.setString(4, currency);
                ps.setInt(5, paidByParticipantId);
                ps.setString(6, category);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                expenseId = rs.next() ? rs.getInt(1) : -1;
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO expense_splits (expense_id, participant_id, amount, is_paid) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<Integer, Double> entry : splits.entrySet()) {
                    ps.setInt(1, expenseId);
                    ps.setInt(2, entry.getKey());
                    ps.setDouble(3, entry.getValue());
                    ps.setInt(4, entry.getKey() == paidByParticipantId ? 1 : 0);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
            Expense exp = new Expense();
            exp.setId(expenseId);
            exp.setGroupId(groupId);
            exp.setDescription(description);
            exp.setTotalAmount(totalAmount);
            exp.setCurrency(currency);
            exp.setPaidByUserId(paidByParticipantId);
            exp.setCategory(category);
            return exp;
        } catch (SQLException e) { connection.rollback(); throw e; }
        finally { connection.setAutoCommit(true); }
    }

    public synchronized List<Expense> getGroupExpenses(int groupId) throws SQLException {
        String sql = """
            SELECT e.id, e.description, e.total_amount, e.currency,
                   e.paid_by_participant, e.category, e.created_at, p.name AS paid_by_name
            FROM expenses e JOIN participants p ON e.paid_by_participant = p.id
            WHERE e.group_id = ?
              AND EXISTS (
                  SELECT 1 FROM expense_splits es
                  WHERE es.expense_id = e.id AND es.is_paid = 0
              )
            ORDER BY e.created_at DESC
        """;
        List<Expense> expenses = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Expense e = new Expense();
                e.setId(rs.getInt("id"));
                e.setGroupId(groupId);
                e.setDescription(rs.getString("description"));
                e.setTotalAmount(rs.getDouble("total_amount"));
                e.setCurrency(rs.getString("currency"));
                e.setPaidByUserId(rs.getInt("paid_by_participant"));
                e.setPaidByUsername(rs.getString("paid_by_name"));
                e.setCategory(rs.getString("category"));
                e.setCreatedAt(rs.getString("created_at"));
                expenses.add(e);
            }
        }
        return expenses;
    }

    /**
     * Returns net balance per participant name.
     * Positive = others owe this person. Negative = this person owes others.
     * Removed participants with outstanding amounts still appear.
     */
    public synchronized Map<String, Double> getGroupBalances(int groupId) throws SQLException {
        Map<String, Double> balances = new LinkedHashMap<>();
        for (Participant p : getParticipants(groupId))
            balances.put(p.getName(), 0.0);

        String sql = """
            SELECT p_payer.name AS payer, p_split.name AS ower, es.amount, es.is_paid
            FROM expenses e
            JOIN participants p_payer ON e.paid_by_participant = p_payer.id
            JOIN expense_splits es ON e.id = es.expense_id
            JOIN participants p_split ON es.participant_id = p_split.id
            WHERE e.group_id = ?
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String payer = rs.getString("payer");
                String ower  = rs.getString("ower");
                double amount = rs.getDouble("amount");
                boolean isPaid = rs.getInt("is_paid") == 1;
                if (!payer.equals(ower) && !isPaid) {
                    balances.merge(payer, amount, Double::sum);
                    balances.merge(ower, -amount, Double::sum);
                }
            }
        }
        return balances;
    }

    /** Returns every bilateral unpaid debt: who owes whom and how much. */
    public synchronized List<String[]> getPairwiseDebts(int groupId) throws SQLException {
        String sql = """
            SELECT p_split.name AS ower,
                   p_payer.name AS owed_to,
                   ROUND(SUM(es.amount), 2) AS amount
            FROM expenses e
            JOIN participants p_payer ON e.paid_by_participant = p_payer.id
            JOIN expense_splits es    ON e.id = es.expense_id
            JOIN participants p_split ON es.participant_id = p_split.id
            WHERE e.group_id = ?
              AND p_payer.name != p_split.name
              AND es.is_paid = 0
            GROUP BY p_split.name, p_payer.name
            HAVING amount > 0.005
            ORDER BY p_split.name, p_payer.name
        """;
        List<String[]> debts = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next())
                debts.add(new String[]{
                    rs.getString("ower"),
                    rs.getString("owed_to"),
                    String.valueOf(rs.getDouble("amount"))
                });
        }
        return debts;
    }

    /** Marks all unpaid splits that owerName owes to owedToName as paid. */
    public synchronized int settlePair(int groupId, String owerName, String owedToName) throws SQLException {
        String sql = """
            UPDATE expense_splits SET is_paid = 1
            WHERE is_paid = 0
              AND participant_id = (SELECT id FROM participants WHERE group_id = ? AND name = ?)
              AND expense_id IN (
                  SELECT id FROM expenses
                  WHERE group_id = ?
                    AND paid_by_participant = (SELECT id FROM participants WHERE group_id = ? AND name = ?)
              )
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);  ps.setString(2, owerName);
            ps.setInt(3, groupId);  ps.setInt(4, groupId);
            ps.setString(5, owedToName);
            return ps.executeUpdate();
        }
    }

    /**
     * Fully settles a participant's account in both directions:
     *   1. What they owe others (their splits in other people's expenses)
     *   2. What others owe them (other people's splits in their expenses)
     * This keeps the balance view consistent — no phantom debts appear after settling.
     */
    public synchronized int settleAll(int groupId, String participantName) throws SQLException {
        // Resolve participant ID once
        int participantId = -1;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM participants WHERE group_id = ? AND name = ?")) {
            ps.setInt(1, groupId);
            ps.setString(2, participantName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) participantId = rs.getInt(1);
        }
        if (participantId == -1) return 0;

        int total = 0;

        // 1. What this participant owes to others (their own splits)
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE expense_splits SET is_paid = 1
                WHERE is_paid = 0
                  AND participant_id = ?
                  AND expense_id IN (SELECT id FROM expenses WHERE group_id = ?)
                """)) {
            ps.setInt(1, participantId);
            ps.setInt(2, groupId);
            total += ps.executeUpdate();
        }

        // 2. What others owe this participant (splits in their expenses)
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE expense_splits SET is_paid = 1
                WHERE is_paid = 0
                  AND expense_id IN (
                      SELECT id FROM expenses
                      WHERE group_id = ? AND paid_by_participant = ?
                  )
                """)) {
            ps.setInt(1, groupId);
            ps.setInt(2, participantId);
            total += ps.executeUpdate();
        }

        return total;
    }

    /** Returns expenses where every split has been paid. */
    public synchronized List<Expense> getPaidGroupExpenses(int groupId) throws SQLException {
        String sql = """
            SELECT e.id, e.description, e.total_amount, e.currency,
                   e.paid_by_participant, e.category, e.created_at, p.name AS paid_by_name
            FROM expenses e JOIN participants p ON e.paid_by_participant = p.id
            WHERE e.group_id = ?
              AND NOT EXISTS (
                  SELECT 1 FROM expense_splits es
                  WHERE es.expense_id = e.id AND es.is_paid = 0
              )
            ORDER BY e.created_at DESC
        """;
        List<Expense> expenses = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Expense e = new Expense();
                e.setId(rs.getInt("id"));
                e.setGroupId(groupId);
                e.setDescription(rs.getString("description"));
                e.setTotalAmount(rs.getDouble("total_amount"));
                e.setCurrency(rs.getString("currency"));
                e.setPaidByUserId(rs.getInt("paid_by_participant"));
                e.setPaidByUsername(rs.getString("paid_by_name"));
                e.setCategory(rs.getString("category"));
                e.setCreatedAt(rs.getString("created_at"));
                expenses.add(e);
            }
        }
        return expenses;
    }

    /** Marks every unpaid split in the group as paid — used when everyone is already net-zero. */
    public synchronized int clearAllDebts(int groupId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE expense_splits SET is_paid = 1 WHERE is_paid = 0 AND expense_id IN (SELECT id FROM expenses WHERE group_id = ?)")) {
            ps.setInt(1, groupId);
            return ps.executeUpdate();
        }
    }

    public synchronized boolean markSplitPaid(int expenseId, int participantId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE expense_splits SET is_paid = 1 WHERE expense_id = ? AND participant_id = ?")) {
            ps.setInt(1, expenseId);
            ps.setInt(2, participantId);
            return ps.executeUpdate() > 0;
        }
    }

    public synchronized Map<String, Double> getCategorySpending(int groupId) throws SQLException {
        String sql = "SELECT category, SUM(total_amount) AS total FROM expenses WHERE group_id = ? GROUP BY category";
        Map<String, Double> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("category"), rs.getDouble("total"));
        }
        return result;
    }

    public synchronized Map<String, Double> getParticipantSpending(int groupId) throws SQLException {
        String sql = """
            SELECT p.name, SUM(e.total_amount) AS total FROM expenses e
            JOIN participants p ON e.paid_by_participant = p.id
            WHERE e.group_id = ? GROUP BY e.paid_by_participant, p.name
        """;
        Map<String, Double> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.put(rs.getString("name"), rs.getDouble("total"));
        }
        return result;
    }

    public synchronized User getUserById(int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, username FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"));
        }
        return null;
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); }
        catch (SQLException e) { e.printStackTrace(); }
    }
}

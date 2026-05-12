package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class BalancePanel extends JPanel {
    private final Client client;
    private int activeGroupId = -1;

    private JLabel groupLabel;
    private JPanel balanceArea;
    private JPanel balanceBody;       // collapsible
    private JButton balanceToggleBtn;
    private boolean balanceExpanded = true;
    private JPanel settlementArea;

    private JSONArray latestBalances = new JSONArray();
    private JSONArray latestPairwise = new JSONArray();

    public BalancePanel(Client client) {
        this.client = client;
        buildUI();
        client.addListener(this::onMessage);
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        groupLabel = new JLabel("Group: None selected");
        groupLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(groupLabel, BorderLayout.NORTH);

        JPanel contentWrapper = new JPanel(new BorderLayout());
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ── Net Balances (collapsible) ────────────────────────────────────────
        balanceToggleBtn = toggleBtn("▼  Net Balances");
        balanceToggleBtn.addActionListener(e -> {
            balanceExpanded = !balanceExpanded;
            balanceBody.setVisible(balanceExpanded);
            balanceToggleBtn.setText((balanceExpanded ? "▼" : "▶") + "  Net Balances");
            content.revalidate();
        });
        content.add(balanceToggleBtn);

        balanceBody = new JPanel();
        balanceBody.setLayout(new BoxLayout(balanceBody, BoxLayout.Y_AXIS));
        balanceBody.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        balanceArea = balanceBody;
        content.add(balanceBody);

        content.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(6));

        // ── Minimum Settlement (always visible, with Settle buttons) ──────────
        content.add(sectionHeader("Minimum Settlement Suggestions"));
        settlementArea = new JPanel();
        settlementArea.setLayout(new BoxLayout(settlementArea, BoxLayout.Y_AXIS));
        content.add(settlementArea);

        contentWrapper.add(content, BorderLayout.NORTH);
        add(new JScrollPane(contentWrapper), BorderLayout.CENTER);

        JButton refreshBtn = styledBtn("Refresh", new Color(80, 80, 80));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(refreshBtn);
        add(south, BorderLayout.SOUTH);

        refreshBtn.addActionListener(e -> refresh());
    }

    public void setActiveGroup(int groupId, String groupName) {
        activeGroupId = groupId;
        groupLabel.setText("Group: " + groupName);
        refresh();
    }

    public void refresh() {
        if (activeGroupId == -1) return;
        client.send("GET_PAIRWISE_DEBTS", new JSONObject().put("groupId", activeGroupId));
        client.send("GET_BALANCES",        new JSONObject().put("groupId", activeGroupId));
        client.send("GET_SETTLEMENTS",     new JSONObject().put("groupId", activeGroupId));
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private void renderBalances() {
        balanceArea.removeAll();
        if (latestBalances.isEmpty()) {
            balanceArea.add(smallLabel("  No data yet.", Color.GRAY));
        } else {
            for (int i = 0; i < latestBalances.length(); i++) {
                JSONObject b = latestBalances.getJSONObject(i);
                balanceArea.add(personCard(b.getString("username"), b.getDouble("balance")));
                balanceArea.add(Box.createVerticalStrut(4));
            }
        }
        balanceArea.revalidate();
        balanceArea.repaint();
    }

    private JPanel personCard(String name, double balance) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 210, 210)),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Header row: name + net balance + Settle All
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setOpaque(false);
        String text;
        if (balance > 0.005)
            text = String.format("<html><b>%s</b>  <span color='#279E60'>is owed $%.2f</span></html>", name, balance);
        else if (balance < -0.005)
            text = String.format("<html><b>%s</b>  <span color='#C0392B'>owes $%.2f total</span></html>", name, -balance);
        else
            text = String.format("<html><b>%s</b>  <span color='#888888'>settled up ✓</span></html>", name);

        JLabel nameLbl = new JLabel(text);
        nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 14));
        header.add(nameLbl, BorderLayout.CENTER);

        JButton settleBtn = styledBtn("Settle All", new Color(41, 128, 185));
        settleBtn.addActionListener(e -> settleAll(name));
        header.add(settleBtn, BorderLayout.EAST);
        card.add(header);

        // Pairwise breakdown under this person
        for (int j = 0; j < latestPairwise.length(); j++) {
            JSONObject d = latestPairwise.getJSONObject(j);
            String ower   = d.getString("ower");
            String owedTo = d.getString("owedTo");
            double amt    = d.getDouble("amount");
            String line = null;
            if (ower.equals(name))
                line = String.format("  ↳ owes %s  $%.2f", owedTo, amt);
            else if (owedTo.equals(name))
                line = String.format("  ↳ %s owes you  $%.2f", ower, amt);
            if (line != null) {
                JLabel sub = new JLabel(line);
                sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
                sub.setForeground(new Color(100, 100, 100));
                sub.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 0));
                card.add(sub);
            }
        }
        return card;
    }

    // ── Message handling ──────────────────────────────────────────────────────

    private void onMessage(JSONObject msg) {
        if (!"RESPONSE".equals(msg.optString("type")) && !"BROADCAST".equals(msg.optString("type"))) return;
        String action = msg.optString("action");

        switch (action) {
            case "GET_PAIRWISE_DEBTS" -> SwingUtilities.invokeLater(() -> {
                latestPairwise = msg.optJSONArray("debts");
                if (latestPairwise == null) latestPairwise = new JSONArray();
                renderBalances();
            });
            case "GET_BALANCES" -> SwingUtilities.invokeLater(() -> {
                latestBalances = msg.optJSONArray("balances");
                if (latestBalances == null) latestBalances = new JSONArray();
                renderBalances();
            });
            case "GET_SETTLEMENTS" -> SwingUtilities.invokeLater(() -> {
                settlementArea.removeAll();
                JSONArray arr = msg.optJSONArray("settlements");
                boolean allSettled = (arr == null || arr.isEmpty());

                if (allSettled) {
                    settlementArea.add(smallLabel("  Everyone is settled up!", new Color(39, 174, 96)));

                    // If net-zero but pairwise debts still exist, offer one-click clear
                    if (latestPairwise != null && !latestPairwise.isEmpty()) {
                        JButton clearBtn = styledBtn("Mark All Remaining Debts as Cleared", new Color(142, 68, 173));
                        clearBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                        clearBtn.addActionListener(e -> {
                            int confirm = JOptionPane.showConfirmDialog(this,
                                "All balances are already net zero.\nMark every remaining split as paid and clear all expenses?",
                                "Clear All", JOptionPane.YES_NO_OPTION);
                            if (confirm == JOptionPane.YES_OPTION)
                                client.send("CLEAR_ALL", new JSONObject().put("groupId", activeGroupId));
                        });
                        settlementArea.add(Box.createVerticalStrut(6));
                        settlementArea.add(clearBtn);
                    }
                } else {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        settlementArea.add(settlementRow(s.getString("from"), s.getString("to"), s.getDouble("amount")));
                        settlementArea.add(Box.createVerticalStrut(4));
                    }
                }
                settlementArea.revalidate();
                settlementArea.repaint();
            });
            case "SETTLE_ALL", "SETTLE_PAIR", "CLEAR_ALL",
                 "ADD_EXPENSE", "EXPENSE_ADDED",
                 "MARK_PAID",  "PAYMENT_MARKED" ->
                SwingUtilities.invokeLater(this::refresh);
        }
    }

    // ── Settlement row with Settle button ─────────────────────────────────────

    private JPanel settlementRow(String from, String to, double amount) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 210, 210)),
            BorderFactory.createEmptyBorder(4, 10, 4, 6)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = new JLabel(String.format("%s  →  %s   $%.2f", from, to, amount));
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 13));
        row.add(lbl, BorderLayout.CENTER);

        JButton btn = styledBtn("Settle", new Color(39, 174, 96));
        btn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                String.format("%s pays %s $%.2f — this is the minimum needed to settle the group.\n" +
                    "Mark all remaining splits as paid and clear all expenses?", from, to, amount),
                "Settle Group", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION)
                client.send("CLEAR_ALL", new JSONObject().put("groupId", activeGroupId));
        });
        row.add(btn, BorderLayout.EAST);
        return row;
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void settleAll(String name) {
        int confirm = JOptionPane.showConfirmDialog(this,
            "Mark ALL of " + name + "'s shares as fully paid?\nThis clears debts in both directions.",
            "Settle All — " + name, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION)
            client.send("SETTLE_ALL", new JSONObject()
                .put("groupId", activeGroupId)
                .put("participantName", name));
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private JButton toggleBtn(String text) {
        JButton btn = new JButton(text);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBackground(new Color(220, 220, 220));
        btn.setForeground(new Color(40, 40, 40));
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(Font.BOLD));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        return btn;
    }

    private JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.BOLD, 14));
        lbl.setBorder(BorderFactory.createEmptyBorder(4, 2, 6, 2));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JLabel smallLabel(String text, Color color) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("SansSerif", Font.ITALIC, 13));
        lbl.setForeground(color);
        lbl.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 4));
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JButton styledBtn(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(2, 8, 2, 8));
        return btn;
    }
}

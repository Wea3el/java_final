package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import com.expensesplitter.currency.CurrencyService;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExpensePanel extends JPanel {
    private final Client client;
    private int activeGroupId = -1;
    private String activeGroupName = "None";

    private static final String[] COLUMNS = {"ID", "Date", "Description", "Amount (USD)", "Paid By", "Category"};

    // Active (unpaid) expenses
    private final DefaultTableModel tableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable table = new JTable(tableModel);

    // Paid expenses (collapsed by default)
    private final DefaultTableModel paidTableModel = new DefaultTableModel(COLUMNS, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable paidTable = new JTable(paidTableModel);
    private JPanel paidSection;
    private JButton togglePaidBtn;
    private boolean paidExpanded = false;

    private JLabel groupLabel;

    // Members fetched from server for the split dialog
    private final List<int[]> members = new ArrayList<>();
    private final List<String> memberNames = new ArrayList<>();

    public ExpensePanel(Client client) {
        this.client = client;
        buildUI();
        client.addListener(this::onMessage);
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        groupLabel = new JLabel("Group: None selected");
        groupLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        add(groupLabel, BorderLayout.NORTH);

        // ── Active expenses table ─────────────────────────────────────────────
        configureTable(table);
        JScrollPane activeScroll = new JScrollPane(table);

        // ── Paid expenses collapsible section ────────────────────────────────
        configureTable(paidTable);
        paidTable.setForeground(Color.GRAY);

        togglePaidBtn = new JButton("▶  Paid Expenses (0)");
        togglePaidBtn.setHorizontalAlignment(SwingConstants.LEFT);
        togglePaidBtn.setOpaque(true);
        togglePaidBtn.setBorderPainted(false);
        togglePaidBtn.setBackground(new Color(220, 220, 220));
        togglePaidBtn.setForeground(new Color(60, 60, 60));
        togglePaidBtn.setFocusPainted(false);
        togglePaidBtn.setFont(togglePaidBtn.getFont().deriveFont(Font.BOLD));

        JScrollPane paidScroll = new JScrollPane(paidTable);
        paidScroll.setPreferredSize(new Dimension(0, 160));

        paidSection = new JPanel(new BorderLayout());
        paidSection.add(paidScroll, BorderLayout.CENTER);
        paidSection.setVisible(false);

        JPanel collapsible = new JPanel(new BorderLayout(0, 0));
        collapsible.add(togglePaidBtn, BorderLayout.NORTH);
        collapsible.add(paidSection, BorderLayout.CENTER);

        // Stack active table above collapsible paid section
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(activeScroll, BorderLayout.CENTER);
        center.add(collapsible, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        // ── Toolbar ───────────────────────────────────────────────────────────
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        JButton addBtn     = styledButton("Add Expense",    new Color(39, 174, 96));
        JButton markBtn    = styledButton("Mark Share Paid", new Color(231, 76, 60));
        JButton refreshBtn = styledButton("Refresh",         Color.DARK_GRAY);
        south.add(addBtn); south.add(markBtn); south.add(refreshBtn);
        add(south, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> {
            if (activeGroupId == -1) { JOptionPane.showMessageDialog(this, "Select a group first."); return; }
            openAddExpenseDialog();
        });
        markBtn.addActionListener(e -> markPaid());
        refreshBtn.addActionListener(e -> refresh());
        togglePaidBtn.addActionListener(e -> {
            paidExpanded = !paidExpanded;
            paidSection.setVisible(paidExpanded);
            // Update arrow indicator
            String current = togglePaidBtn.getText();
            togglePaidBtn.setText(current.replace(paidExpanded ? "▶" : "▼",
                                                   paidExpanded ? "▼" : "▶"));
            revalidate();
        });
    }

    private void configureTable(JTable t) {
        t.setRowHeight(24);
        t.getColumnModel().getColumn(0).setMaxWidth(50);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    }

    public void setActiveGroup(int groupId, String groupName) {
        activeGroupId = groupId;
        activeGroupName = groupName;
        groupLabel.setText("Group: " + groupName);
        members.clear();
        memberNames.clear();
        client.send("GET_GROUP_MEMBERS", new JSONObject().put("groupId", groupId));
        refresh();
    }

    public void refresh() {
        if (activeGroupId == -1) return;
        client.send("LIST_EXPENSES",      new JSONObject().put("groupId", activeGroupId));
        client.send("LIST_PAID_EXPENSES", new JSONObject().put("groupId", activeGroupId));
    }

    private void markPaid() {
        int row = table.getSelectedRow();
        if (row == -1) { JOptionPane.showMessageDialog(this, "Select an expense first."); return; }
        if (memberNames.isEmpty()) { JOptionPane.showMessageDialog(this, "Members not loaded yet."); return; }
        int expenseId = (int) tableModel.getValueAt(row, 0);

        // Ask which participant's share to mark paid
        String[] names = memberNames.toArray(new String[0]);
        String chosen = (String) JOptionPane.showInputDialog(
            this, "Mark paid for which participant?", "Mark Share Paid",
            JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
        if (chosen == null) return;

        int participantId = members.get(memberNames.indexOf(chosen))[0];
        client.send("MARK_PAID", new JSONObject()
            .put("expenseId", expenseId)
            .put("userId", participantId)
            .put("groupId", activeGroupId));
    }

    private void openAddExpenseDialog() {
        if (memberNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Members not loaded yet. Please wait and try again.");
            return;
        }
        AddExpenseDialog dialog = new AddExpenseDialog(
            (JFrame) SwingUtilities.getWindowAncestor(this),
            client, activeGroupId, members, memberNames);
        dialog.setVisible(true);
    }

    private void onMessage(JSONObject msg) {
        if (!"RESPONSE".equals(msg.optString("type")) && !"BROADCAST".equals(msg.optString("type"))) return;
        String action = msg.optString("action");

        switch (action) {
            case "LIST_EXPENSES" -> SwingUtilities.invokeLater(() -> {
                tableModel.setRowCount(0);
                JSONArray arr = msg.optJSONArray("expenses");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        tableModel.addRow(new Object[]{
                            e.getInt("id"),
                            e.optString("createdAt", "").substring(0, Math.min(10, e.optString("createdAt","").length())),
                            e.getString("description"),
                            String.format("$%.2f", e.getDouble("totalAmount")),
                            e.getString("paidBy"),
                            e.getString("category")
                        });
                    }
                }
            });
            case "GET_GROUP_MEMBERS" -> SwingUtilities.invokeLater(() -> {
                members.clear();
                memberNames.clear();
                JSONArray arr = msg.optJSONArray("members");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        members.add(new int[]{m.getInt("id")});
                        memberNames.add(m.getString("username"));
                    }
                }
            });
            case "LIST_PAID_EXPENSES" -> SwingUtilities.invokeLater(() -> {
                paidTableModel.setRowCount(0);
                JSONArray arr = msg.optJSONArray("expenses");
                int count = 0;
                if (arr != null) {
                    count = arr.length();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        paidTableModel.addRow(new Object[]{
                            e.getInt("id"),
                            e.optString("createdAt", "").substring(0, Math.min(10, e.optString("createdAt","").length())),
                            e.getString("description"),
                            String.format("$%.2f", e.getDouble("totalAmount")),
                            e.getString("paidBy"),
                            e.getString("category")
                        });
                    }
                }
                String arrow = paidExpanded ? "▼" : "▶";
                togglePaidBtn.setText(arrow + "  Paid Expenses (" + count + ")");
            });
            case "ADD_EXPENSE", "MARK_PAID", "SETTLE_ALL",
                 "EXPENSE_ADDED", "PAYMENT_MARKED" ->
                SwingUtilities.invokeLater(this::refresh);
        }
    }

    private JButton styledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        return btn;
    }

    // ── Inner Dialog ──────────────────────────────────────────────────────────

    private static class AddExpenseDialog extends JDialog {
        private final Client client;
        private final int groupId;
        private final List<int[]> members;
        private final List<String> memberNames;

        private JTextField descField, amountField;
        private JComboBox<String> currencyCombo, categoryCombo, paidByCombo;
        private JCheckBox equalSplitCheck;
        private List<JCheckBox> includeChecks;
        private List<JTextField> splitFields;

        AddExpenseDialog(JFrame parent, Client client, int groupId,
                         List<int[]> members, List<String> memberNames) {
            super(parent, "Add Expense", true);
            this.client = client;
            this.groupId = groupId;
            this.members = new ArrayList<>(members);
            this.memberNames = new ArrayList<>(memberNames);
            buildUI();
            pack();
            setMinimumSize(new Dimension(420, 300));
            setLocationRelativeTo(parent);
        }

        private void buildUI() {
            setLayout(new BorderLayout(10, 10));
            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 5, 4, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.WEST;

            int row = 0;
            form.add(lbl("Description:"), gc(gbc, 0, row));
            descField = new JTextField(20);
            form.add(descField, gc(gbc, 1, row++));

            form.add(lbl("Amount:"), gc(gbc, 0, row));
            amountField = new JTextField("", 10);
            amountField.setToolTipText("Enter amount (e.g. 42.50)");
            form.add(amountField, gc(gbc, 1, row++));

            form.add(lbl("Currency:"), gc(gbc, 0, row));
            currencyCombo = new JComboBox<>(CurrencyService.getSupportedCurrencies());
            form.add(currencyCombo, gc(gbc, 1, row++));

            form.add(lbl("Category:"), gc(gbc, 0, row));
            categoryCombo = new JComboBox<>(new String[]{
                "General","Food","Transport","Housing","Entertainment","Utilities","Health","Other"});
            form.add(categoryCombo, gc(gbc, 1, row++));

            form.add(lbl("Paid by:"), gc(gbc, 0, row));
            paidByCombo = new JComboBox<>(memberNames.toArray(new String[0]));
            form.add(paidByCombo, gc(gbc, 1, row++));

            // Divider
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            form.add(new JSeparator(), gbc);
            gbc.gridwidth = 1;

            // "Split among" header + equal-split toggle
            equalSplitCheck = new JCheckBox("Equal split", true);
            JPanel splitHeader = new JPanel(new BorderLayout());
            JLabel splitLbl = new JLabel("Split among:");
            splitLbl.setFont(splitLbl.getFont().deriveFont(Font.BOLD));
            splitHeader.add(splitLbl, BorderLayout.WEST);
            splitHeader.add(equalSplitCheck, BorderLayout.EAST);
            gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
            form.add(splitHeader, gbc);
            gbc.gridwidth = 1;

            // One row per participant: [checkbox name]  [amount field]
            includeChecks = new ArrayList<>();
            splitFields   = new ArrayList<>();
            for (int i = 0; i < memberNames.size(); i++) {
                JCheckBox cb = new JCheckBox(memberNames.get(i), true);
                JTextField tf = new JTextField("0.00", 8);
                tf.setEnabled(false); // disabled when equal split is on
                includeChecks.add(cb);
                splitFields.add(tf);

                gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 1.0;
                form.add(cb, gbc);
                gbc.gridx = 1; gbc.weightx = 0;
                form.add(tf, gbc);
                row++;

                // Recalculate when a checkbox is toggled
                cb.addActionListener(e -> recalcEqual());
            }
            gbc.weightx = 0;

            // Recalculate when total or equal-split toggle changes
            amountField.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { recalcEqual(); }
            });
            equalSplitCheck.addActionListener(e -> {
                boolean eq = equalSplitCheck.isSelected();
                splitFields.forEach(tf -> tf.setEnabled(!eq));
                if (eq) recalcEqual();
            });

            recalcEqual(); // initial fill

            add(new JScrollPane(form), BorderLayout.CENTER);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok     = colorBtn("Add",    new Color(39, 174, 96), Color.WHITE);
            JButton cancel = colorBtn("Cancel", new Color(180, 180, 180), Color.BLACK);
            buttons.add(cancel);
            buttons.add(ok);
            add(buttons, BorderLayout.SOUTH);

            ok.addActionListener(e -> submit());
            cancel.addActionListener(e -> dispose());
        }

        /**
         * Divide total evenly among checked participants.
         * The last checked person absorbs the remainder so splits always sum exactly to total.
         * e.g. $100 ÷ 3 → $33.33, $33.33, $33.34
         */
        private void recalcEqual() {
            if (!equalSplitCheck.isSelected()) return;
            double total = 0;
            try { total = Double.parseDouble(amountField.getText().trim()); } catch (NumberFormatException ignored) {}

            List<Integer> checked = new ArrayList<>();
            for (int i = 0; i < includeChecks.size(); i++)
                if (includeChecks.get(i).isSelected()) checked.add(i);
            if (checked.isEmpty()) return;

            int n = checked.size();
            long totalCents = Math.round(total * 100);
            long baseCents  = totalCents / n;
            long remainder  = totalCents % n;

            for (int i = 0; i < includeChecks.size(); i++) {
                if (!includeChecks.get(i).isSelected()) {
                    splitFields.get(i).setText("0.00");
                } else {
                    // Last checked participant gets any leftover cents
                    boolean isLast = (i == checked.get(n - 1));
                    long cents = baseCents + (isLast ? remainder : 0);
                    splitFields.get(i).setText(String.format("%.2f", cents / 100.0));
                }
            }
        }

        private void submit() {
            try {
                String desc = descField.getText().trim();
                if (desc.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter a description."); return; }
                double total = Double.parseDouble(amountField.getText().trim());
                String currency = (String) currencyCombo.getSelectedItem();
                String category = (String) categoryCombo.getSelectedItem();
                int paidById = members.get(paidByCombo.getSelectedIndex())[0];

                // Collect only checked participants
                JSONArray splits = new JSONArray();
                for (int i = 0; i < members.size(); i++) {
                    if (!includeChecks.get(i).isSelected()) continue;
                    double amt = Double.parseDouble(splitFields.get(i).getText().trim());
                    splits.put(new JSONObject().put("userId", members.get(i)[0]).put("amount", amt));
                }
                if (splits.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Select at least one participant.");
                    return;
                }

                client.send("ADD_EXPENSE", new JSONObject()
                    .put("groupId", groupId)
                    .put("description", desc)
                    .put("totalAmount", total)
                    .put("currency", currency)
                    .put("category", category)
                    .put("paidBy", paidById)
                    .put("splits", splits));
                dispose();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number format.");
            }
        }

        private JLabel lbl(String text) { return new JLabel(text); }
        private GridBagConstraints gc(GridBagConstraints base, int x, int y) {
            base.gridx = x; base.gridy = y; return base;
        }
        private JButton colorBtn(String text, Color bg, Color fg) {
            JButton b = new JButton(text);
            b.setOpaque(true); b.setBorderPainted(false);
            b.setBackground(bg); b.setForeground(fg); b.setFocusPainted(false);
            return b;
        }
    }
}

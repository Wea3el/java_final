package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    private final Client client = new Client();
    private JLabel userLabel;
    private JTabbedPane tabs;

    private GroupPanel groupPanel;
    private ExpensePanel expensePanel;
    private BalancePanel balancePanel;
    private DashboardPanel dashboardPanel;

    public MainFrame() {
        super("ExpenseSplitter");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 620));

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignored) {}

        showLoginScreen();
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void showLoginScreen() {
        getContentPane().removeAll();
        LoginPanel loginPanel = new LoginPanel(client, this::onLoginSuccess);
        loginPanel.setPreferredSize(new Dimension(420, 320));
        getContentPane().add(loginPanel);
        revalidate();
        repaint();
    }

    private void onLoginSuccess(String username) {
        buildMainUI(username);
        groupPanel.refresh();
    }

    private void buildMainUI(String username) {
        getContentPane().removeAll();
        setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new BorderLayout(10, 5));
        header.setBackground(new Color(41, 128, 185));
        header.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        JLabel title = new JLabel("ExpenseSplitter");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        userLabel = new JLabel("Logged in as: " + username);
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        userLabel.setForeground(new Color(200, 230, 255));
        header.add(title, BorderLayout.WEST);
        header.add(userLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Tabs
        groupPanel    = new GroupPanel(client, this::onGroupSelected);
        expensePanel  = new ExpensePanel(client);
        balancePanel  = new BalancePanel(client);
        dashboardPanel = new DashboardPanel(client);

        tabs = new JTabbedPane();
        tabs.addTab("Groups",    icon("G"), groupPanel);
        tabs.addTab("Expenses",  icon("E"), expensePanel);
        tabs.addTab("Balances",  icon("B"), balancePanel);
        tabs.addTab("Dashboard", icon("D"), dashboardPanel);
        add(tabs, BorderLayout.CENTER);

        // Status bar that shows real-time notifications
        JLabel statusBar = new JLabel(" ");
        statusBar.setBorder(BorderFactory.createEtchedBorder());
        statusBar.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(statusBar, BorderLayout.SOUTH);

        client.addListener(msg -> {
            if ("BROADCAST".equals(msg.optString("type"))) {
                String action = msg.optString("action");
                String by = msg.optString("addedBy", msg.optString("markedBy", "Someone"));
                String notice = switch (action) {
                    case "EXPENSE_ADDED"   -> by + " added a new expense.";
                    case "PAYMENT_MARKED"  -> by + " marked a payment.";
                    default -> "";
                };
                if (!notice.isEmpty())
                    SwingUtilities.invokeLater(() -> statusBar.setText("  🔔 " + notice));
            }
        });

        revalidate();
        repaint();
        setSize(900, 650);
        setLocationRelativeTo(null);
    }

    private void onGroupSelected(int groupId, String groupName) {
        expensePanel.setActiveGroup(groupId, groupName);
        balancePanel.setActiveGroup(groupId, groupName);
        dashboardPanel.setActiveGroup(groupId, groupName);
        tabs.setSelectedIndex(1); // jump to Expenses tab
    }

    private Icon icon(String letter) {
        return new Icon() {
            public void paintIcon(Component c, java.awt.Graphics g, int x, int y) {
                g.setColor(new Color(41, 128, 185));
                g.fillOval(x, y, 14, 14);
                g.setColor(Color.WHITE);
                g.setFont(new Font("SansSerif", Font.BOLD, 9));
                g.drawString(letter, x + 4, y + 10);
            }
            public int getIconWidth()  { return 14; }
            public int getIconHeight() { return 14; }
        };
    }
}

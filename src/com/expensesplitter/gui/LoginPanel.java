package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class LoginPanel extends JPanel {
    private final Client client;
    private final Consumer<String> onLoginSuccess; // receives the username

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    public LoginPanel(Client client, Consumer<String> onLoginSuccess) {
        this.client = client;
        this.onLoginSuccess = onLoginSuccess;
        buildUI();
        client.addListener(this::onMessage);
    }

    private void buildUI() {
        setLayout(new GridBagLayout());
        setBackground(new Color(245, 247, 250));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel title = new JLabel("ExpenseSplitter", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(new Color(41, 128, 185));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        add(title, gbc);

        JLabel sub = new JLabel("Collaborative Expense Tracker", SwingConstants.CENTER);
        sub.setFont(new Font("SansSerif", Font.PLAIN, 13));
        sub.setForeground(Color.GRAY);
        gbc.gridy = 1;
        add(sub, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2; gbc.gridx = 0;
        add(darkLabel("Username:"), gbc);
        gbc.gridx = 1;
        usernameField = new JTextField(16);
        usernameField.setForeground(Color.BLACK);
        usernameField.setBackground(Color.WHITE);
        add(usernameField, gbc);

        gbc.gridy = 3; gbc.gridx = 0;
        add(darkLabel("Password:"), gbc);
        gbc.gridx = 1;
        passwordField = new JPasswordField(16);
        passwordField.setForeground(Color.BLACK);
        passwordField.setBackground(Color.WHITE);
        add(passwordField, gbc);

        JButton loginBtn = colorButton("Login", new Color(41, 128, 185), Color.WHITE);
        gbc.gridy = 4; gbc.gridx = 0; gbc.gridwidth = 2;
        add(loginBtn, gbc);

        JButton registerBtn = colorButton("Register New Account", new Color(236, 240, 241), Color.BLACK);
        gbc.gridy = 5;
        add(registerBtn, gbc);

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setForeground(Color.RED);
        gbc.gridy = 6;
        add(statusLabel, gbc);

        loginBtn.addActionListener(e -> sendAuth("LOGIN"));
        registerBtn.addActionListener(e -> sendAuth("REGISTER"));
        passwordField.addActionListener(e -> sendAuth("LOGIN"));
    }

    private JButton colorButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        return btn;
    }

    private JLabel darkLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(Color.BLACK);
        return lbl;
    }

    private void sendAuth(String type) {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter username and password.");
            return;
        }
        statusLabel.setText("Connecting...");
        statusLabel.setForeground(Color.GRAY);
        try {
            if (!client.isConnected()) client.connect();
            client.send(type, new JSONObject().put("username", username).put("password", password));
        } catch (Exception ex) {
            statusLabel.setText("Cannot connect to server: " + ex.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }

    private void onMessage(JSONObject msg) {
        String type = msg.optString("type");
        String action = msg.optString("action");
        if (!"RESPONSE".equals(type)) return;
        if (!"LOGIN".equals(action) && !"REGISTER".equals(action)) return;

        SwingUtilities.invokeLater(() -> {
            if (msg.optBoolean("success")) {
                onLoginSuccess.accept(msg.optString("username", "User"));
            } else {
                statusLabel.setText(msg.optString("message", "Authentication failed"));
                statusLabel.setForeground(Color.RED);
            }
        });
    }
}

package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;

public class DashboardPanel extends JPanel {
    private final Client client;
    private int activeGroupId = -1;

    private JLabel groupLabel;
    private JPanel chartArea;

    // Live chart datasets
    private final DefaultPieDataset<String> pieDataset = new DefaultPieDataset<>();
    private final DefaultCategoryDataset barDataset = new DefaultCategoryDataset();

    public DashboardPanel(Client client) {
        this.client = client;
        buildUI();
        client.addListener(this::onMessage);
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel top = new JPanel(new BorderLayout());
        groupLabel = new JLabel("Group: None selected");
        groupLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        top.add(groupLabel, BorderLayout.WEST);
        JButton refreshBtn = new JButton("Refresh Charts");
        refreshBtn.setBackground(Color.DARK_GRAY);
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> refresh());
        top.add(refreshBtn, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        // Pie chart — spending by category
        JFreeChart pieChart = ChartFactory.createPieChart(
            "Spending by Category", pieDataset, true, true, false);
        ChartPanel piePanel = new ChartPanel(pieChart);
        piePanel.setPreferredSize(new Dimension(400, 300));

        // Bar chart — spending by user
        JFreeChart barChart = ChartFactory.createBarChart(
            "Amount Paid by Each Member", "Member", "Amount (USD)",
            barDataset, PlotOrientation.VERTICAL, true, true, false);
        ChartPanel barPanel = new ChartPanel(barChart);
        barPanel.setPreferredSize(new Dimension(400, 300));

        chartArea = new JPanel(new GridLayout(1, 2, 10, 10));
        chartArea.add(piePanel);
        chartArea.add(barPanel);
        add(chartArea, BorderLayout.CENTER);

        JLabel note = new JLabel("All amounts shown in USD", SwingConstants.CENTER);
        note.setFont(new Font("SansSerif", Font.ITALIC, 11));
        note.setForeground(Color.GRAY);
        add(note, BorderLayout.SOUTH);
    }

    public void setActiveGroup(int groupId, String groupName) {
        activeGroupId = groupId;
        groupLabel.setText("Group: " + groupName);
        refresh();
    }

    public void refresh() {
        if (activeGroupId == -1) return;
        client.send("GET_CATEGORY_SPENDING", new JSONObject().put("groupId", activeGroupId));
        client.send("GET_USER_SPENDING",     new JSONObject().put("groupId", activeGroupId));
    }

    private void onMessage(JSONObject msg) {
        if (!"RESPONSE".equals(msg.optString("type"))) return;
        String action = msg.optString("action");

        switch (action) {
            case "GET_CATEGORY_SPENDING" -> SwingUtilities.invokeLater(() -> {
                pieDataset.clear();
                JSONObject data = msg.optJSONObject("data");
                if (data != null)
                    for (String key : data.keySet())
                        pieDataset.setValue(key, data.getDouble(key));
                chartArea.repaint();
            });
            case "GET_USER_SPENDING" -> SwingUtilities.invokeLater(() -> {
                barDataset.clear();
                JSONObject data = msg.optJSONObject("data");
                if (data != null)
                    for (String key : data.keySet())
                        barDataset.addValue(data.getDouble(key), "Paid", key);
                chartArea.repaint();
            });
            case "EXPENSE_ADDED" -> SwingUtilities.invokeLater(this::refresh);
        }
    }
}

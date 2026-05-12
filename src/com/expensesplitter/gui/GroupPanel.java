package com.expensesplitter.gui;

import com.expensesplitter.client.Client;
import com.expensesplitter.model.Group;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class GroupPanel extends JPanel {
    private final Client client;
    private final BiConsumer<Integer, String> onGroupSelected;

    private final DefaultListModel<Group> groupModel = new DefaultListModel<>();
    private final JList<Group> groupList = new JList<>(groupModel);

    private final DefaultListModel<String> memberModel = new DefaultListModel<>();
    private final JList<String> memberList = new JList<>(memberModel);
    private final List<Integer> memberIds = new ArrayList<>();

    private int viewingGroupId = -1;
    private JLabel membersTitle;
    private JLabel activeGroupLabel;

    public GroupPanel(Client client, BiConsumer<Integer, String> onGroupSelected) {
        this.client = client;
        this.onGroupSelected = onGroupSelected;
        buildUI();
        client.addListener(this::onMessage);
    }

    private void buildUI() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Groups & Members");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(title, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setResizeWeight(0.5);

        // ── Left: group list ──────────────────────────────────────────────────
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        JLabel groupsTitle = new JLabel("Your Groups");
        groupsTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        leftPanel.add(groupsTitle, BorderLayout.NORTH);

        groupList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        leftPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);

        JPanel groupBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        groupBtns.add(btn("Select",    new Color(41, 128, 185),  e -> selectGroup()));
        groupBtns.add(btn("Create",    new Color(39, 174, 96),   e -> createGroup()));
        groupBtns.add(btn("Join by ID",new Color(142, 68, 173),  e -> joinGroup()));
        groupBtns.add(btn("Refresh",   new Color(80, 80, 80),    e -> refresh()));
        leftPanel.add(groupBtns, BorderLayout.SOUTH);
        split.setLeftComponent(leftPanel);

        // ── Right: member list ────────────────────────────────────────────────
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        membersTitle = new JLabel("Members — select a group");
        membersTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        rightPanel.add(membersTitle, BorderLayout.NORTH);

        memberList.setFont(new Font("SansSerif", Font.PLAIN, 14));
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rightPanel.add(new JScrollPane(memberList), BorderLayout.CENTER);

        JPanel memberBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        memberBtns.add(btn("Add Member",    new Color(39, 174, 96),  e -> addMember()));
        memberBtns.add(btn("Remove Member", new Color(192, 57, 43),  e -> removeMember()));
        rightPanel.add(memberBtns, BorderLayout.SOUTH);
        split.setRightComponent(rightPanel);

        add(split, BorderLayout.CENTER);

        activeGroupLabel = new JLabel("No group selected");
        activeGroupLabel.setFont(new Font("SansSerif", Font.ITALIC, 12));
        activeGroupLabel.setForeground(Color.GRAY);
        add(activeGroupLabel, BorderLayout.SOUTH);

        // Clicking a group loads its members immediately
        groupList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && groupList.getSelectedValue() != null) {
                Group g = groupList.getSelectedValue();
                viewingGroupId = g.getId();
                membersTitle.setText("Members — " + g.getName());
                client.send("GET_GROUP_MEMBERS", new JSONObject().put("groupId", g.getId()));
            }
        });
    }

    public void refresh() {
        client.send("LIST_GROUPS", null);
    }

    private void selectGroup() {
        Group g = groupList.getSelectedValue();
        if (g == null) { JOptionPane.showMessageDialog(this, "Select a group first."); return; }
        client.send("SET_ACTIVE_GROUP", new JSONObject().put("groupId", g.getId()));
        activeGroupLabel.setText("Active: " + g.getName());
        onGroupSelected.accept(g.getId(), g.getName());
    }

    private void createGroup() {
        String name = JOptionPane.showInputDialog(this, "Group name:", "Create Group", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty())
            client.send("CREATE_GROUP", new JSONObject().put("name", name.trim()));
    }

    private void joinGroup() {
        String s = JOptionPane.showInputDialog(this, "Enter Group ID:", "Join Group", JOptionPane.PLAIN_MESSAGE);
        if (s != null) {
            try { client.send("JOIN_GROUP", new JSONObject().put("groupId", Integer.parseInt(s.trim()))); }
            catch (NumberFormatException ex) { JOptionPane.showMessageDialog(this, "Invalid group ID."); }
        }
    }

    private void addMember() {
        if (viewingGroupId == -1) { JOptionPane.showMessageDialog(this, "Select a group first."); return; }
        String username = JOptionPane.showInputDialog(this, "Enter participant name:", "Add Member", JOptionPane.PLAIN_MESSAGE);
        if (username != null && !username.trim().isEmpty())
            client.send("ADD_MEMBER", new JSONObject()
                .put("groupId", viewingGroupId)
                .put("username", username.trim()));
    }

    private void removeMember() {
        if (viewingGroupId == -1) { JOptionPane.showMessageDialog(this, "Select a group first."); return; }
        int idx = memberList.getSelectedIndex();
        if (idx == -1) { JOptionPane.showMessageDialog(this, "Select a member to remove."); return; }
        String username = memberModel.getElementAt(idx);
        int userId = memberIds.get(idx);
        int confirm = JOptionPane.showConfirmDialog(this,
            "Remove \"" + username + "\" from this group?\n" +
            "Their past expense data will be preserved.", "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION)
            client.send("REMOVE_MEMBER", new JSONObject()
                .put("groupId", viewingGroupId)
                .put("userId", userId));
    }

    private void onMessage(JSONObject msg) {
        String type   = msg.optString("type");
        String action = msg.optString("action");
        if (!"RESPONSE".equals(type) && !"BROADCAST".equals(type)) return;

        switch (action) {
            case "LIST_GROUPS" -> SwingUtilities.invokeLater(() -> {
                groupModel.clear();
                JSONArray arr = msg.optJSONArray("groups");
                if (arr != null)
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject g = arr.getJSONObject(i);
                        groupModel.addElement(new Group(g.getInt("id"), g.getString("name"), 0));
                    }
            });
            case "GET_GROUP_MEMBERS" -> SwingUtilities.invokeLater(() -> {
                memberModel.clear();
                memberIds.clear();
                JSONArray arr = msg.optJSONArray("members");
                if (arr != null)
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject m = arr.getJSONObject(i);
                        memberIds.add(m.getInt("id"));
                        memberModel.addElement(m.getString("username"));
                    }
            });
            case "CREATE_GROUP", "JOIN_GROUP" -> SwingUtilities.invokeLater(() -> {
                if (msg.optBoolean("success")) refresh();
                else JOptionPane.showMessageDialog(this, msg.optString("message", "Operation failed"));
            });
            case "ADD_MEMBER", "REMOVE_MEMBER" -> SwingUtilities.invokeLater(() -> {
                if (msg.optBoolean("success")) {
                    if (viewingGroupId != -1)
                        client.send("GET_GROUP_MEMBERS", new JSONObject().put("groupId", viewingGroupId));
                } else {
                    JOptionPane.showMessageDialog(this, msg.optString("message", "Operation failed"));
                }
            });
            // Broadcast from another client — refresh member list live
            case "MEMBER_ADDED", "MEMBER_REMOVED" -> SwingUtilities.invokeLater(() -> {
                if (viewingGroupId != -1 && msg.optInt("groupId") == viewingGroupId)
                    client.send("GET_GROUP_MEMBERS", new JSONObject().put("groupId", viewingGroupId));
            });
        }
    }

    private JButton btn(String text, Color bg, java.awt.event.ActionListener action) {
        JButton b = new JButton(text);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.addActionListener(action);
        return b;
    }
}

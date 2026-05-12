package com.expensesplitter.model;

public class Expense {
    private int id;
    private int groupId;
    private String description;
    private double totalAmount;
    private String currency;
    private int paidByUserId;
    private String paidByUsername;
    private String category;
    private String createdAt;

    public Expense() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGroupId() { return groupId; }
    public void setGroupId(int groupId) { this.groupId = groupId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getPaidByUserId() { return paidByUserId; }
    public void setPaidByUserId(int paidByUserId) { this.paidByUserId = paidByUserId; }

    public String getPaidByUsername() { return paidByUsername; }
    public void setPaidByUsername(String paidByUsername) { this.paidByUsername = paidByUsername; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}

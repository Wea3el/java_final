package com.expensesplitter.model;

public class Settlement {
    private final String fromUser;
    private final String toUser;
    private final double amount;

    public Settlement(String fromUser, String toUser, double amount) {
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.amount = amount;
    }

    public String getFromUser() { return fromUser; }
    public String getToUser() { return toUser; }
    public double getAmount() { return amount; }

    @Override
    public String toString() {
        return String.format("%s pays %s $%.2f", fromUser, toUser, amount);
    }
}

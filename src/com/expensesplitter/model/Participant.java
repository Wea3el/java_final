package com.expensesplitter.model;

public class Participant {
    private final int id;
    private final int groupId;
    private final String name;

    public Participant(int id, int groupId, String name) {
        this.id = id;
        this.groupId = groupId;
        this.name = name;
    }

    public int getId() { return id; }
    public int getGroupId() { return groupId; }
    public String getName() { return name; }

    @Override
    public String toString() { return name; }
}

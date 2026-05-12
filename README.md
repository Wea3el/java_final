# ExpenseSplitter

A real-time, multi-user expense tracking application for roommates and travel groups. Built in Java using a client-server architecture — multiple users connect to a shared server, log expenses, and see balances update live across all connected clients.

## CS6103 · Introduction to Java · Spring 2026 · Wesley Zhuang

---

## Features

- **Multi-user, real-time** — multiple clients connect simultaneously; balance updates broadcast instantly to everyone in the group
- **Expense groups** — create household or trip groups and invite members by group ID
- **Flexible splits** — split any expense equally or enter custom amounts per person
- **Smart debt settlement** — computes the minimum number of transfers needed to settle all debts (greedy O(n) algorithm)
- **Mark as paid** — track which shares have been settled; balances update automatically
- **Trip mode** — enter expenses in any currency (USD, EUR, GBP, JPY, and more); live exchange rates fetched from a public API with offline fallback
- **Visual dashboard** — spending breakdown by category (pie chart) and by member (bar chart) powered by JFreeChart

---

## Requirements

- **Java 17+** (tested on OpenJDK 25)
- **curl** (pre-installed on macOS/Linux) — used by `setup.sh` to download JARs
- Internet access for the first run (to download dependencies and for live exchange rates)

---

## Quick Start

### 1. Download dependencies

```bash
./setup.sh
```

Downloads three JARs into `lib/` from Maven Central:

- `sqlite-jdbc` — SQLite database driver
- `jfreechart` — chart rendering
- `json` — JSON protocol library

### 2. Compile

```bash
./compile.sh
```

### 3. Start the server

```bash
./run_server.sh
```

The server starts on **port 9090** and creates `expensesplitter.db` (SQLite) on first run. Running this script again automatically stops any previous instance.

### 4. Start one or more clients

Open a new terminal window for each user:

```bash
./run_client.sh
```

---

## Using the App

### Login / Register

On first launch, register a new account. Subsequent launches use the same credentials (stored in the database). Multiple users can register and run clients simultaneously.

### Groups tab

- **Create Group** — enter a name; you are automatically added as a member
- **Join by ID** — enter the numeric group ID shown next to any group name (share this with friends)
- **Select Group** — highlight a group and click Select to make it active; all other tabs update automatically

### Expenses tab

- **Add Expense** — enter description, amount, currency, category, and who paid; choose equal split or enter custom amounts per member
- **Mark My Share Paid** — select an expense row and click to record your payment; the payer's balance updates for everyone in real time

### Balances tab

Shows each member's net balance (green = owed money, red = owes money) and the minimum set of transfers needed to fully settle the group.

### Dashboard tab

Two live charts for the active group:

- **Pie chart** — total spending broken down by category
- **Bar chart** — total amount paid by each member

---

## Project Structure

```text
java_final/
├── setup.sh              # Downloads JAR dependencies
├── compile.sh            # Compiles all source files
├── run_server.sh         # Starts the server (kills any previous instance)
├── run_client.sh         # Starts a client GUI
├── lib/                  # JAR dependencies (created by setup.sh)
├── out/                  # Compiled classes (created by compile.sh)
└── src/com/expensesplitter/
    ├── ClientApp.java              # Client entry point
    ├── server/
    │   ├── Server.java             # Listens on port 9090, manages client threads
    │   └── ClientHandler.java      # Per-client thread; handles all message routing
    ├── db/
    │   └── DatabaseManager.java    # JDBC + SQLite; all queries are synchronized
    ├── model/
    │   ├── User.java
    │   ├── Group.java
    │   ├── Expense.java
    │   └── Settlement.java
    ├── algorithm/
    │   └── DebtSettler.java        # Greedy min-transfer settlement algorithm
    ├── currency/
    │   └── CurrencyService.java    # Live exchange rates via open.er-api.com
    ├── client/
    │   └── Client.java             # Socket client with async listener callbacks
    └── gui/
        ├── MainFrame.java          # Root window; routes server messages to panels
        ├── LoginPanel.java
        ├── GroupPanel.java
        ├── ExpensePanel.java       # Includes AddExpenseDialog inner class
        ├── BalancePanel.java
        └── DashboardPanel.java
```

---

## Architecture

```text
┌─────────────┐   JSON over TCP    ┌──────────────────────────────┐
│  Client GUI │ ◄─────────────────►│  Server (port 9090)          │
│  (Swing)    │                    │  ┌────────────────────────┐  │
└─────────────┘                    │  │  ClientHandler thread  │  │
                                   │  │  (one per connection)  │  │
┌─────────────┐   JSON over TCP    │  └────────────────────────┘  │
│  Client GUI │ ◄─────────────────►│  ┌────────────────────────┐  │
│  (Swing)    │                    │  │  DatabaseManager       │  │
└─────────────┘                    │  │  (SQLite, synchronized) │  │
                                   │  └────────────────────────┘  │
                                   └──────────────────────────────┘
```

- **Networking** — raw Java sockets; each message is a single JSON line terminated by `\n`
- **Concurrency** — one `ClientHandler` thread per connection via `Executors.newCachedThreadPool()`; all database methods are `synchronized`; live broadcasts use `ConcurrentHashMap` + `CopyOnWriteArraySet`
- **Database** — SQLite in WAL mode with a 5-second busy timeout; transactions with `setAutoCommit(false)` guard multi-step writes (e.g., creating a group also inserts the creator as a member atomically)
- **Settlement algorithm** — two priority queues (max-heap for creditors, min-heap for debtors); greedily matches the largest creditor with the largest debtor each step, producing at most n−1 transfers for n people
- **Currency** — `CurrencyService` fetches rates from `open.er-api.com`; results are cached in-memory for the server's lifetime; hardcoded fallback rates activate if the API is unreachable

---

## Database Schema

```sql
users          (id, username, password_hash)
groups         (id, name, created_by)
group_members  (group_id, user_id)
expenses       (id, group_id, description, total_amount, currency, paid_by, category, created_at)
expense_splits (id, expense_id, user_id, amount, is_paid)
```

Passwords are hashed with SHA-256 before storage. All monetary amounts are converted to USD on the server before being written to the database.

---

## Dependencies

| Library | Version | Purpose |
| --- | --- | --- |
| [sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) | 3.46.1.3 | SQLite database driver |
| [JFreeChart](https://www.jfree.org/jfreechart/) | 1.5.4 | Pie and bar charts |
| [org.json](https://github.com/stleary/JSON-java) | 20240303 | JSON serialization for the client-server protocol |

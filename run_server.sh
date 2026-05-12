#!/usr/bin/env bash
# Starts the ExpenseSplitter server

ROOT="$(cd "$(dirname "$0")" && pwd)"
LIB="$ROOT/lib"
CP="$ROOT/out:$LIB/sqlite-jdbc-3.46.1.3.jar:$LIB/jfreechart-1.5.4.jar:$LIB/json-20240303.jar"

# Kill any previous instance holding port 9090
OLD=$(lsof -ti :9090 2>/dev/null)
if [ -n "$OLD" ]; then
  echo "Stopping previous server (PID $OLD)..."
  kill "$OLD" 2>/dev/null
  sleep 1
fi

echo "Starting server on port 9090..."
java --enable-native-access=ALL-UNNAMED -cp "$CP" com.expensesplitter.server.Server

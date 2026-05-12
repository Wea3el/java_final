#!/usr/bin/env bash
# Starts the ExpenseSplitter client GUI

ROOT="$(cd "$(dirname "$0")" && pwd)"
LIB="$ROOT/lib"
CP="$ROOT/out:$LIB/sqlite-jdbc-3.46.1.3.jar:$LIB/jfreechart-1.5.4.jar:$LIB/json-20240303.jar"

java --enable-native-access=ALL-UNNAMED -cp "$CP" com.expensesplitter.ClientApp

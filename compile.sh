#!/usr/bin/env bash
# Compiles the entire project

set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/out"
LIB="$ROOT/lib"

# Verify deps
for jar in sqlite-jdbc-3.46.1.3.jar jfreechart-1.5.4.jar json-20240303.jar; do
  if [ ! -f "$LIB/$jar" ]; then
    echo "Missing: lib/$jar — run ./setup.sh first"
    exit 1
  fi
done

mkdir -p "$OUT"

CP="$LIB/sqlite-jdbc-3.46.1.3.jar:$LIB/jfreechart-1.5.4.jar:$LIB/json-20240303.jar"

echo "Compiling..."
find "$SRC" -name "*.java" | xargs javac --release 17 -cp "$CP" -d "$OUT"
echo "Done. Classes in out/"

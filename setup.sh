#!/usr/bin/env bash
# Downloads required JAR dependencies from Maven Central into lib/

set -e
mkdir -p lib

BASE="https://repo1.maven.org/maven2"

download() {
  local url="$1"
  local dest="lib/$(basename "$url")"
  if [ -f "$dest" ]; then
    echo "Already present: $dest"
  else
    echo "Downloading: $(basename "$url") ..."
    curl -sL "$url" -o "$dest"
    echo "  -> $dest"
  fi
}

download "$BASE/org/xerial/sqlite-jdbc/3.46.1.3/sqlite-jdbc-3.46.1.3.jar"
download "$BASE/org/jfree/jfreechart/1.5.4/jfreechart-1.5.4.jar"
download "$BASE/org/json/json/20240303/json-20240303.jar"

echo ""
echo "All dependencies ready in lib/"

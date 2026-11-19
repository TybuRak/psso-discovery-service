#!/usr/bin/env bash
# Starts the discovery service on the lab computer.
# Usage: ./run.sh            (port 8080)
#        PORT=80 ./run.sh    (another port)
cd "$(dirname "$0")"
JAR=$(ls discovery-service*.jar target/discovery-service*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
  echo "Brak pliku discovery-service*.jar obok run.sh ani w target/." >&2
  exit 1
fi
if ! command -v java >/dev/null 2>&1; then
  echo "Brak Javy (potrzebna 17+). Instrukcja w README, sekcja 'Pracownia'." >&2
  exit 1
fi
echo "IP tego komputera: $(hostname -I 2>/dev/null || ipconfig getifaddr en0)"
exec java -jar "$JAR"

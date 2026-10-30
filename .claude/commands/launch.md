---
description: Launch the TripleA General Staff local API (loopback JSON server) on a persistent random port
allowed-tools: Bash
---

## Context
- Port file: !`cat .port 2>/dev/null || echo "not set yet"`
- Working directory: !`pwd`

## Task

Launch the General Staff local API (`./gradlew :general-staff:run --args="serve"`), a backend
service, so use the 4000-4999 port range:

1. Generate a persistent random port if `.port` doesn't exist:
   [ -f .port ] || { while true; do P=$((RANDOM % 1000 + 4000)); lsof -i :$P >/dev/null 2>&1 || { echo $P > .port; break; }; done; }

2. Read the port: PORT=$(cat .port)

3. Kill any stale process holding the port (prevents EADDRINUSE on re-launch):
   lsof -ti :$PORT | xargs kill -9 2>/dev/null || true

4. Download any iCloud-evicted (dataless) files so the server doesn't stall on first-touch reads
   (this repo lives on the SSD, where the check finds nothing; keep it for folders that stay on iCloud):
   N=$(find . -flags +dataless 2>/dev/null | wc -l | tr -d ' '); if [ "${N:-0}" -gt 0 ]; then echo "Downloading $N iCloud-evicted files…"; brctl download . 2>/dev/null || true; find . -flags +dataless -print0 2>/dev/null | xargs -0 -n 25 -P 8 cat > /dev/null; fi

5. Start the server on that port in the background. No JDK is on the PATH; use the Homebrew one:
   export JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home
   mkdir -p build && (./gradlew :general-staff:run --args="serve --port $PORT" > build/general-staff-serve.log 2>&1 &)

6. Wait until `curl -s http://127.0.0.1:$PORT/health` answers (the first Gradle start compiles and
   can take a minute), then open the browser: open "http://localhost:$PORT"

7. Tell the user the URL (http://localhost:<port>), that `GET /` lists the endpoints, and that
   `build/general-staff-serve.log` holds the server output.

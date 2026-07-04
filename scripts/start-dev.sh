#!/usr/bin/env bash
# start-dev.sh — start the backend and the widget demo (Linux/macOS)
# Usage:  ./scripts/start-dev.sh
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Starting help-chat backend on http://localhost:8090 ..."
(cd "$ROOT/backend" && mvn spring-boot:run) &
BACKEND_PID=$!

echo "Starting widget demo on http://localhost:3000/demo.html ..."
(cd "$ROOT/widget" && python3 -m http.server 3000) &
DEMO_PID=$!

trap "kill $BACKEND_PID $DEMO_PID 2>/dev/null" EXIT

echo ""
echo "Backend : http://localhost:8090/chat/config/demo"
echo "Demo    : http://localhost:3000/demo.html"
echo "Press Ctrl+C to stop both."
wait

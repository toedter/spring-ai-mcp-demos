#!/usr/bin/env bash
#
# Starts the full Spring AI MCP demo stack:
#   1. MCP Authorization Server  (http://localhost:9000)
#   2. MCP Server                (http://localhost:8082)
#   3. MCP Client                (http://localhost:8083)
#   4. MCP Chatbot (Angular)     (http://localhost:4200)
#
# All services run as background jobs of this script. Logs are written to
# ./logs/<service>.log. Press Ctrl+C (or kill this script) to stop everything.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOG_DIR="$ROOT_DIR/logs"
mkdir -p "$LOG_DIR"

PIDS=()

cleanup() {
  echo
  echo "Stopping all services..."
  for pid in "${PIDS[@]:-}"; do
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
    fi
  done
  wait 2>/dev/null || true
}
trap cleanup EXIT INT TERM

echo "=========================================================="
echo " Starting Spring AI MCP Demo stack"
echo "=========================================================="

echo "[1/4] Starting MCP Authorization Server (http://localhost:9000) ..."
./gradlew :mcp-authorization-server:bootRun > "$LOG_DIR/mcp-authorization-server.log" 2>&1 &
PIDS+=($!)

echo "      Waiting for the authorization server to come up..."
sleep 15

echo "[2/4] Starting MCP Server (http://localhost:8082) ..."
./gradlew :mcp-server:bootRun > "$LOG_DIR/mcp-server.log" 2>&1 &
PIDS+=($!)

echo "      Waiting for the MCP server to come up..."
sleep 10

echo "[3/4] Starting MCP Client (http://localhost:8083) ..."
./gradlew :mcp-client:bootRun > "$LOG_DIR/mcp-client.log" 2>&1 &
PIDS+=($!)

echo "[4/4] Starting MCP Chatbot (http://localhost:4200) ..."
(
  cd mcp-chatbot
  if [ ! -d node_modules ]; then
    npm install
  fi
  npm start
) > "$LOG_DIR/mcp-chatbot.log" 2>&1 &
PIDS+=($!)

echo
echo "All services started. Logs are in $LOG_DIR/*.log"
echo "Press Ctrl+C to stop all services."

wait


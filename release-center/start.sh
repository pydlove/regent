#!/bin/bash
set -euo pipefail

export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  . "$NVM_DIR/nvm.sh"
fi
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RUNTIME_DIR="$HOME/.regent/release-center"
PID_FILE="$RUNTIME_DIR/pid"
LOG_FILE="$HOME/.regent/logs/release-center.log"
PORT="${REGENT_RELEASE_CENTER_PORT:-12600}"
LABEL="com.pydlove.regent.release-center"
SERVICE="$PROJECT_DIR/release-center/service.sh"
ENV_FILE="$RUNTIME_DIR/service.env"

mkdir -p "$RUNTIME_DIR" "$HOME/.regent/logs"

EXISTING_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [ -n "$EXISTING_PID" ]; then
  echo "发布中心已在运行：http://127.0.0.1:$PORT/（PID ${EXISTING_PID}）"
  if [ "${1:-}" != "--no-open" ]; then open "http://127.0.0.1:$PORT/"; fi
  exit 0
fi

cd "$PROJECT_DIR"
printf 'REGENT_RELEASE_CENTER_PORT=%s\n' "$PORT" > "$ENV_FILE"

if [ "$(uname -s)" = "Darwin" ]; then
  launchctl remove "$LABEL" >/dev/null 2>&1 || true
  launchctl submit -l "$LABEL" -- "$SERVICE"
else
  nohup "$SERVICE" > "$LOG_FILE" 2>&1 < /dev/null &
  SERVER_PID=$!
  echo "$SERVER_PID" > "$PID_FILE"
  disown "$SERVER_PID" 2>/dev/null || true
fi

for _ in $(seq 1 30); do
  if curl -fsS --max-time 1 "http://127.0.0.1:$PORT/api/state" >/dev/null 2>&1; then
    SERVER_PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
    if [ -n "$SERVER_PID" ]; then echo "$SERVER_PID" > "$PID_FILE"; fi
    echo "发布中心已启动：http://127.0.0.1:$PORT/"
    echo "PID：${SERVER_PID:-未知}"
    echo "日志：$LOG_FILE"
    if [ "${1:-}" != "--no-open" ]; then open "http://127.0.0.1:$PORT/"; fi
    exit 0
  fi
  sleep 0.3
done

echo "发布中心启动失败，最后 40 行日志："
tail -40 "$LOG_FILE"
exit 1

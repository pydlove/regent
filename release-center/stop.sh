#!/bin/bash
set -euo pipefail

RUNTIME_DIR="$HOME/.regent/release-center"
PID_FILE="$RUNTIME_DIR/pid"
PORT="${REGENT_RELEASE_CENTER_PORT:-12600}"
LABEL="com.pydlove.regent.release-center"

STATE="$(curl -fsS --max-time 2 "http://127.0.0.1:$PORT/api/state" 2>/dev/null || true)"
case "$STATE" in
  *'"busy":true'*)
    echo "有发布任务正在执行，不能停止发布中心"
    exit 1
    ;;
esac

if [ "$(uname -s)" = "Darwin" ]; then
  launchctl remove "$LABEL" >/dev/null 2>&1 || true
fi

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
if [ -z "$PID" ]; then
  PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
fi

if [ -z "$PID" ]; then
  echo "发布中心未运行"
  exit 0
fi

kill "$PID" 2>/dev/null || true
rm -f "$PID_FILE"
echo "发布中心已停止（PID ${PID}）"

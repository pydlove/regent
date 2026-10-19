#!/bin/bash
set -euo pipefail

PORT="${REGENT_RELEASE_CENTER_PORT:-12600}"
PID="$(lsof -tiTCP:"$PORT" -sTCP:LISTEN 2>/dev/null | head -1 || true)"
if [ -z "$PID" ]; then
  echo "发布中心未运行"
  exit 1
fi
echo "发布中心运行中：http://127.0.0.1:$PORT/（PID ${PID}）"

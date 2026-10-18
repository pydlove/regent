#!/bin/sh
set -eu

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8787}"
API_BASE_URL="${API_BASE_URL%/}"

echo "检查外部执行者协议"
JSON=$(curl -fsS --max-time 10 "${API_BASE_URL}/api/agent/contract")
printf '%s' "$JSON" | grep -q '"protocol":"regent-agent/1"' || {
  echo "FAIL JSON 契约里的协议版本不正确" >&2
  exit 1
}

MARKDOWN=$(curl -fsS --max-time 10 "${API_BASE_URL}/api/agent/contract.md")
printf '%s' "$MARKDOWN" | grep -q 'regent-agent/1' || {
  echo "FAIL Markdown 契约里缺少协议版本" >&2
  exit 1
}

echo "PASS 外部执行者公开协议"

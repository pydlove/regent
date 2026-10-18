#!/bin/sh
set -eu

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8787}"
API_BASE_URL="${API_BASE_URL%/}"
URL="${API_BASE_URL}/actuator/health"

echo "检查健康接口：${URL}"
BODY=$(curl -fsS --max-time 10 "$URL")
echo "$BODY"

if ! printf '%s' "$BODY" | grep -q '"status":"UP"'; then
  echo "FAIL 健康接口没有返回 status=UP" >&2
  exit 1
fi

echo "PASS 后端健康接口"

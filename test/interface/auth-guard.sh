#!/bin/sh
set -eu

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:8787}"
API_BASE_URL="${API_BASE_URL%/}"
URL="${API_BASE_URL}/api/test-cases"

STATUS=$(curl -sS --max-time 10 -o /dev/null -w '%{http_code}' "$URL")
echo "未登录访问 ${URL}：HTTP ${STATUS}"

if [ "$STATUS" != "401" ]; then
  echo "FAIL 管理接口未登录时应返回 401，实际为 ${STATUS}" >&2
  exit 1
fi

echo "PASS 管理接口登录保护"

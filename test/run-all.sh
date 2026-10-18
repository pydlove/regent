#!/bin/sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)

node "$ROOT/web/app-shell-smoke.mjs"
node "$ROOT/web/api-proxy-smoke.mjs"
sh "$ROOT/interface/health-api.sh"
sh "$ROOT/interface/public-api.sh"
sh "$ROOT/interface/auth-guard.sh"

echo "全部样例测试通过"

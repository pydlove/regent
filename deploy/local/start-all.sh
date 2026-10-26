#!/bin/bash
# 一键起本地全套：后端 + 前端 + 已配置的公网中继连接器。
source "$(dirname "$0")/_common.sh"

"$SCRIPT_DIR/start-backend.sh"
echo
"$SCRIPT_DIR/start-frontend.sh"
echo
"$SCRIPT_DIR/start-relay.sh"
echo
"$SCRIPT_DIR/device-agent.sh" ensure
echo
echo "========================================"
echo "  前端  http://127.0.0.1:$FRONTEND_PORT/"
echo "  后端  http://127.0.0.1:$BACKEND_PORT/actuator/health"
echo "  日志  $LOG_DIR/"
echo "  停止  deploy/local/stop-all.sh"
echo "========================================"

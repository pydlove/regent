#!/bin/bash
# 停掉本地前端与后端。
source "$(dirname "$0")/_common.sh"

stop_by_pidfile "前端" "$FRONTEND_PID_FILE"
stop_by_pidfile "后端" "$BACKEND_PID_FILE"
if relay_agent_loaded; then
    warn "公网中继连接器由 launchd 托管，未停止（要停：deploy/local/relay-agent.sh uninstall）"
else
    stop_by_pidfile "公网中继连接器" "$RELAY_PID_FILE"
fi

# pid 文件可能丢（比如手动起过），兜底按端口清理
for pair in "前端:$FRONTEND_PORT" "后端:$BACKEND_PORT"; do
    name=${pair%%:*}; port=${pair##*:}
    pid=$(listen_pid "$port")
    if [ -n "$pid" ]; then
        warn "$name 仍在端口 $port 上监听 (PID: $pid)，一并结束"
        kill "$pid" 2>/dev/null || true
        sleep 1
        kill -9 "$pid" 2>/dev/null || true
    fi
done
echo "本地服务已全部停止"

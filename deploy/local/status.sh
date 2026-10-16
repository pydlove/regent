#!/bin/bash
# 看本地服务状态。端口探活优先于 pid 文件 —— pid 文件会过期，端口不会撒谎。
source "$(dirname "$0")/_common.sh"

check() {
    local name=$1 port=$2 pid_file=$3 health=${4:-}
    local pid; pid=$(listen_pid "$port")
    if [ -z "$pid" ]; then
        if [ -f "$pid_file" ]; then
            fail "$name  未运行（端口 $port 无监听，pid 文件残留）"
        else
            fail "$name  未运行"
        fi
        return
    fi
    if [ -n "$health" ] && ! curl -fsS --max-time 2 "$health" >/dev/null 2>&1; then
        warn "$name  端口在听但健康检查没通过 (PID: $pid)"
        return
    fi
    info "$name  运行中 (PID: ${pid}，端口 $port)"
}

echo "Regent 本地服务状态"
echo "----------------------------------------"
check "后端" "$BACKEND_PORT" "$BACKEND_PID_FILE" "http://127.0.0.1:$BACKEND_PORT/actuator/health"
check "前端" "$FRONTEND_PORT" "$FRONTEND_PID_FILE" "http://127.0.0.1:$FRONTEND_PORT/"
if relay_agent_loaded; then
    info "公网中继连接器  launchd 托管中"
elif [ -f "$RELAY_PID_FILE" ] && pid_alive "$(cat "$RELAY_PID_FILE")"; then
    info "公网中继连接器  运行中 (PID: $(cat "$RELAY_PID_FILE"))"
else
    if [ -f "$RELAY_ENV_FILE" ]; then
        warn "公网中继连接器  未运行（已配对但没起，跑 deploy/local/start-relay.sh）"
    else
        warn "公网中继连接器  未运行（未配对，先跑 deploy/local/connect-relay.sh）"
    fi
fi
echo "----------------------------------------"
printf '%b\n' "${DIM}日志目录 ${LOG_DIR}${NC}"

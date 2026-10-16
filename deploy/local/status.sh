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
echo "----------------------------------------"
printf '%b\n' "${DIM}日志目录 ${LOG_DIR}${NC}"

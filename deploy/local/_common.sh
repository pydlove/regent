#!/bin/bash
# 本地脚本共用部分。被 start-*.sh / stop-all.sh / status.sh source。
#
# 运行时产物（pid、日志）一律写 ~/.regent/，**不写仓库**：
# 根 .gitignore 里写得很清楚 —— 「仓库里出现这些文件即为配置错误」。
# 所以这里刻意不按通用模板放在脚本同级目录。

set -euo pipefail

RUNTIME_DIR="${REGENT_HOME:-$HOME/.regent}"
LOG_DIR="$RUNTIME_DIR/logs"
mkdir -p "$LOG_DIR"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# deploy/local/ → 上溯两级才是项目根
PROJECT_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"

BACKEND_PID_FILE="$RUNTIME_DIR/backend.pid"
FRONTEND_PID_FILE="$RUNTIME_DIR/frontend.pid"
RELAY_PID_FILE="$RUNTIME_DIR/relay-connector.pid"
BACKEND_LOG="$LOG_DIR/backend.log"
FRONTEND_LOG="$LOG_DIR/frontend.log"
RELAY_LOG="$LOG_DIR/relay-connector.log"
RELAY_ENV_FILE="$RUNTIME_DIR/relay-connector.env"

BACKEND_PORT=8787
FRONTEND_PORT=12580

# 中继连接器的 launchd 标签。托管与 nohup 两套方式必须互斥，
# 否则同一条配置会被连两份，服务器侧看到的连接数会莫名翻倍。
RELAY_AGENT_LABEL="com.pydlove.regent.relay-connector"

relay_agent_loaded() {
    [ "$(uname -s)" = "Darwin" ] && launchctl print "gui/$(id -u)/$RELAY_AGENT_LABEL" >/dev/null 2>&1
}

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[0;33m'; DIM='\033[2m'; NC='\033[0m'

# 用 printf 而不是 echo -e：macOS 的 sh 是 bash 的 POSIX 模式，echo 不认 -e，
# 会原样打出「-e ✓」这种噪音。printf '%b' 在 bash / sh 下行为一致。
info()  { printf '%b\n' "${GREEN}✓${NC} $*"; }
warn()  { printf '%b\n' "${YELLOW}!${NC} $*"; }
fail()  { printf '%b\n' "${RED}✗${NC} $*"; }

# 取端口上的监听进程；lsof 查不到时返回空串。
# 末尾的 || true 是必需的：开了 pipefail 之后，head 提前关闭管道会让 lsof 收到 SIGPIPE，
# 整条管道变成失败，set -e 会把脚本直接干掉 —— 表现为「脚本什么都不说就退出 1」。
listen_pid() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1 || true
}

# 进程活着就算占用，pid 文件只是辅助 —— 本机重启后 pid 文件会残留且可能被复用
pid_alive() {
    [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null
}

wait_http() {
    local url=$1 tries=${2:-40} i=0
    while [ "$i" -lt "$tries" ]; do
        if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
        sleep 0.5; i=$((i + 1))
    done
    return 1
}

stop_by_pidfile() {
    local name=$1 pid_file=$2
    if [ ! -f "$pid_file" ]; then warn "$name 未在运行（无 pid 文件）"; return 0; fi
    local pid; pid=$(cat "$pid_file")
    if pid_alive "$pid"; then
        echo "停止 $name (PID: $pid)..."
        kill "$pid" 2>/dev/null || true
        local i=0
        while pid_alive "$pid" && [ "$i" -lt 20 ]; do sleep 0.5; i=$((i + 1)); done
        if pid_alive "$pid"; then
            warn "$name 没有响应 TERM，强制结束"
            kill -9 "$pid" 2>/dev/null || true
        fi
        info "$name 已停止"
    else
        warn "$name 的 pid 文件已失效（进程不存在）"
    fi
    rm -f "$pid_file"
}

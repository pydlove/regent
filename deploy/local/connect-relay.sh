#!/bin/bash
# 配对公网中继：读取服务器共享密钥 → 写 ~/.regent/relay-connector.env → 重启连接器 → 探活。
#
# 直接运行即可，会交互询问服务器 IP / SSH 端口 / 用户名。优先用 SSH 密钥；
# 密钥不通且是交互终端时才询问密码。非交互场景可用环境变量：
#   REGENT_SERVER_IP / REGENT_SSH_PORT / REGENT_SERVER_USER
#   REGENT_SSH_KEY（指定私钥）或 REGENT_SERVER_PASSWORD（需 sshpass）
# deploy/remote/deploy.sh 会直接传入 REGENT_RELAY_SHARED_SECRET，此时完全不碰 SSH。
#
# 中文紧跟变量时必须写 ${VAR}，见 deploy/local/_common.sh 的说明。
export LC_CTYPE=UTF-8
source "$(dirname "$0")/_common.sh"

SERVER_IP="${REGENT_SERVER_IP:-}"
SSH_PORT="${REGENT_SSH_PORT:-}"
SERVER_USER="${REGENT_SERVER_USER:-}"
SERVER_PASSWORD="${REGENT_SERVER_PASSWORD:-}"
SSH_KEY="${REGENT_SSH_KEY:-}"
PUBLIC_PORT="${REGENT_RELAY_PUBLIC_PORT:-18787}"
REMOTE_BASE="${REGENT_REMOTE_BASE:-/root/app/regent-relay}"
SECRET="${REGENT_RELAY_SHARED_SECRET:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

require_tty() {
    if [ ! -t 0 ]; then
        fail "缺少 $1，且当前不是交互终端；请用环境变量提供它。"
        exit 1
    fi
}

ask() {
    # $1 提示语，$2 默认值（可空）；答案写入 REPLY
    local input
    if [ -n "${2:-}" ]; then
        read -r -p "  $1 [${2}]: " input
        REPLY="${input:-${2}}"
    else
        read -r -p "  $1: " input
        REPLY="$input"
    fi
}

if [ -z "$SECRET" ]; then
    if [ -t 0 ] && { [ -z "$SERVER_IP" ] || [ -z "$SSH_PORT" ] || [ -z "$SERVER_USER" ]; }; then
        printf '%s\n' "请输入服务器连接信息（直接回车使用默认值）："
    fi

    if [ -z "$SERVER_IP" ]; then
        require_tty REGENT_SERVER_IP
        while :; do
            ask "服务器 IP"
            if [ -n "$REPLY" ]; then SERVER_IP="$REPLY"; break; fi
            warn "服务器 IP 不能为空"
        done
    fi

    if [ -z "$SSH_PORT" ]; then
        if [ -t 0 ]; then ask "SSH 端口" "22"; SSH_PORT="$REPLY"; else SSH_PORT="22"; fi
    fi

    if [ -z "$SERVER_USER" ]; then
        if [ -t 0 ]; then ask "SSH 用户名" "root"; SERVER_USER="$REPLY"; else SERVER_USER="root"; fi
    fi
fi

if [ -z "$SERVER_IP" ] && [ -z "${REGENT_RELAY_SERVER_URL:-}" ]; then
    fail "缺少服务器地址：请设置 REGENT_SERVER_IP 或 REGENT_RELAY_SERVER_URL"
    exit 1
fi

if [ "$SERVER_IP" = "127.0.0.1" ]; then
    fail "服务器 IP 不能是 127.0.0.1，请填公网服务器地址。"
    exit 1
fi

REMOTE_HOST="${SERVER_USER}@${SERVER_IP}"

# ssh 选项在两条路径（密钥 / 密码）下必须一致，避免「密钥失败是因为没带 StrictHostKeyChecking」这类假故障。
SSH_OPTS=(-p "${SSH_PORT:-22}" -o StrictHostKeyChecking=no -o ConnectTimeout=15 -o NumberOfPasswordPrompts=1)

build_ssh_command() {
    if [ -n "$SERVER_PASSWORD" ]; then
        command -v sshpass >/dev/null || {
            fail "密码认证需要 sshpass：brew install hudochenkov/sshpass/sshpass"
            exit 1
        }
        export SSHPASS="$SERVER_PASSWORD"
        SSH_COMMAND=(sshpass -e ssh "${SSH_OPTS[@]}" "$REMOTE_HOST")
    elif [ -n "$SSH_KEY" ]; then
        SSH_COMMAND=(ssh -i "$SSH_KEY" -o BatchMode=yes "${SSH_OPTS[@]}" "$REMOTE_HOST")
    else
        # 不指定 -i：走 ~/.ssh/config 与默认身份，避免默认私钥名不匹配就误判为「连接失败」
        SSH_COMMAND=(ssh -o BatchMode=yes "${SSH_OPTS[@]}" "$REMOTE_HOST")
    fi
}

probe_ssh() { "${SSH_COMMAND[@]}" "echo ok" >/dev/null 2>&1; }

if [ -z "$SECRET" ]; then
    build_ssh_command
    if ! probe_ssh; then
        if [ -z "$SERVER_PASSWORD" ] && [ -t 0 ]; then
            warn "密钥登录不通，改用密码认证"
            read -r -s -p "  SSH 密码: " SERVER_PASSWORD
            echo
            build_ssh_command
        fi
    fi
    if ! probe_ssh; then
        fail "SSH 连接失败：${REMOTE_HOST}（端口 ${SSH_PORT:-22}）"
        exit 1
    fi
    info "SSH 连接正常：${REMOTE_HOST}"

    SECRET=$("${SSH_COMMAND[@]}" "grep -m1 '^REGENT_RELAY_SHARED_SECRET=' $REMOTE_BASE/relay/relay.env 2>/dev/null | cut -d= -f2- || true")
    if [ -z "$SECRET" ]; then
        fail "在服务器上读不到 $REMOTE_BASE/relay/relay.env 的共享密钥"
        warn "先在服务器侧初始化：sh deploy/remote/deploy.sh"
        exit 1
    fi
    info "已读取服务器共享密钥"
fi

SERVER_URL="${REGENT_RELAY_SERVER_URL:-http://${SERVER_IP}:${PUBLIC_PORT}}"
LOCAL_URL="${REGENT_RELAY_LOCAL_URL:-http://127.0.0.1:${BACKEND_PORT}}"
CONNECTIONS="${REGENT_RELAY_CONNECTIONS:-4}"

mkdir -p "$RUNTIME_DIR"
if [ -f "$RELAY_ENV_FILE" ]; then
    BACKUP="$RELAY_ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
    cp "$RELAY_ENV_FILE" "$BACKUP"
    info "已备份原连接器配置：$BACKUP"
fi
# umask 077：密钥只落在用户目录且仅本人可读，仓库里不出现任何凭据
umask 077
cat > "$RELAY_ENV_FILE" <<EOF
# 由 deploy/local/connect-relay.sh 生成；该文件只存在本机 ~/.regent/ 下，永不入库
REGENT_RELAY_SERVER_URL=$SERVER_URL
REGENT_RELAY_LOCAL_URL=$LOCAL_URL
REGENT_RELAY_SHARED_SECRET=$SECRET
REGENT_RELAY_CONNECTIONS=$CONNECTIONS
EOF
info "已写入 $RELAY_ENV_FILE"

# 已在运行的连接器持有旧配置，必须重启才会重新握手
if relay_agent_loaded; then
    bash "$SCRIPT_DIR/relay-agent.sh" restart
else
    stop_by_pidfile "公网中继连接器" "$RELAY_PID_FILE"
    bash "$SCRIPT_DIR/start-relay.sh"
fi

info "探活中继：${SERVER_URL}/health"
for _ in $(seq 1 15); do
    HEALTH=$(curl -fsS --max-time 5 "$SERVER_URL/health" 2>/dev/null || true)
    COMPACT=$(printf '%s' "$HEALTH" | tr -d ' ')
    case "$COMPACT" in
        *'"connectorOnline":true'*)
            info "中继已连通：$COMPACT"
            if [ -z "$(listen_pid "$BACKEND_PORT")" ]; then
                warn "本机 ${BACKEND_PORT} 还没在跑，公网访问会 502；先执行 ./deploy/local/start-all.sh"
            fi
            exit 0
            ;;
    esac
    sleep 2
done

fail "连接器未在服务器上线；最后 20 行日志："
tail -20 "$RELAY_LOG" 2>/dev/null || true
exit 1

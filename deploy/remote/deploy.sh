#!/bin/bash
set -euo pipefail

# 非 UTF-8 locale 下，部分 bash（3.2/5.x 均有记录）会把中文首字节并入
# 相邻的变量名，导致 unbound variable。统一固定为 UTF-8 规避该陷阱；
# 注意中文紧跟变量时仍必须写 ${VAR} 花括号，两者缺一不可。
export LC_CTYPE=UTF-8

# ===================================================================
# Regent 公网中继一键部署
#
# 服务器只运行中继和静态前端；真正的 Regent 仍在本机 127.0.0.1:8787。
# 本机连接器主动访问服务器，因此服务器不需要反向连接本机。
#
# 直接运行即可：会交互询问服务器 IP、SSH 端口、用户名、密码。
# 非交互场景用 REGENT_SERVER_IP / REGENT_SSH_PORT / REGENT_SERVER_USER /
# REGENT_SERVER_PASSWORD（密码）或 REGENT_SSH_KEY（密钥）跳过询问。
# ===================================================================

SERVER_IP="${REGENT_SERVER_IP:-}"
SERVER_USER="${REGENT_SERVER_USER:-}"
SERVER_PASSWORD="${REGENT_SERVER_PASSWORD:-}"
SSH_KEY_PATH="${REGENT_SSH_KEY:-$HOME/.ssh/id_rsa}"
SSH_PORT="${REGENT_SSH_PORT:-}"

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$DEPLOY_DIR")")"
APP_NAME="regent-relay"
REMOTE_BASE="${REGENT_REMOTE_BASE:-/root/app/$APP_NAME}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
log_info()  { printf '%b\n' "${GREEN}[INFO]${NC} $*"; }
log_warn()  { printf '%b\n' "${YELLOW}[WARN]${NC} $*"; }
log_error() { printf '%b\n' "${RED}[ERROR]${NC} $*"; }

case "$REMOTE_BASE" in
    /*) ;;
    *) log_error "REGENT_REMOTE_BASE 必须是绝对路径：$REMOTE_BASE"; exit 1 ;;
esac
case "$REMOTE_BASE" in
    *[!A-Za-z0-9._/-]*) log_error "REGENT_REMOTE_BASE 含不支持的字符：$REMOTE_BASE"; exit 1 ;;
esac
REMOTE_BASE="${REMOTE_BASE%/}"
if [ -z "$REMOTE_BASE" ] || [ "$REMOTE_BASE" = "/" ]; then
    log_error "REGENT_REMOTE_BASE 不能是根目录"
    exit 1
fi
case "/$REMOTE_BASE/" in
    *'/../'*|*'/./'*|*'//'*)
        log_error "REGENT_REMOTE_BASE 不能包含 .、.. 或重复斜杠"
        exit 1
        ;;
esac

require_tty() {
    if [ ! -t 0 ]; then
        log_error "缺少 $1，且当前不是交互终端；请先用环境变量提供它。"
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

if [ -t 0 ] && { [ -z "$SERVER_IP" ] || [ -z "$SSH_PORT" ] || [ -z "$SERVER_USER" ]; }; then
    printf '%s\n' "请输入服务器连接信息（直接回车使用默认值）："
fi

if [ -z "$SERVER_IP" ]; then
    require_tty REGENT_SERVER_IP
    while :; do
        ask "服务器 IP"
        if [ -n "$REPLY" ]; then SERVER_IP="$REPLY"; break; fi
        log_warn "服务器 IP 不能为空"
    done
fi

if [ "$SERVER_IP" = "127.0.0.1" ]; then
    log_error "服务器 IP 不能是 127.0.0.1，请填公网服务器地址。"
    exit 1
fi
case "$SERVER_IP" in
    ''|*[!A-Za-z0-9.-]*) log_error "服务器 IP 格式不正确：$SERVER_IP"; exit 1 ;;
esac

if [ -z "$SSH_PORT" ]; then
    if [ -t 0 ]; then
        ask "SSH 端口" "22"
        SSH_PORT="$REPLY"
    else
        SSH_PORT="22"
    fi
fi
case "$SSH_PORT" in
    ''|*[!0-9]*) log_error "SSH 端口必须是数字：$SSH_PORT"; exit 1 ;;
esac

if [ -z "$SERVER_USER" ]; then
    if [ -t 0 ]; then
        ask "SSH 用户名" "root"
        SERVER_USER="$REPLY"
    else
        SERVER_USER="root"
    fi
fi

if [ -z "$SERVER_PASSWORD" ]; then
    if [ -t 0 ]; then
        read -r -s -p "  SSH 密码（直接回车改用密钥登录）: " SERVER_PASSWORD
        echo
    fi
fi

REMOTE_HOST="$SERVER_USER@$SERVER_IP"
if [ -n "$SERVER_PASSWORD" ]; then
    log_info "目标：${REMOTE_HOST}（SSH 端口 ${SSH_PORT}，密码认证）"
else
    log_info "目标：${REMOTE_HOST}（SSH 端口 ${SSH_PORT}，密钥认证 ${SSH_KEY_PATH}）"
fi

SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=no -o ConnectTimeout=30 -o NumberOfPasswordPrompts=1)
if [ -n "$SERVER_PASSWORD" ]; then
    if command -v sshpass >/dev/null 2>&1; then
        export SSHPASS="$SERVER_PASSWORD"
        SSH_COMMAND=(sshpass -e ssh "${SSH_OPTS[@]}" "$REMOTE_HOST")
        RSYNC_SSH="sshpass -e ssh -p $SSH_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=30 -o NumberOfPasswordPrompts=1"
    else
        # Web 调用没有 TTY。OpenSSH 在 SSH_ASKPASS_REQUIRE=force 时会调用辅助脚本，
        # 密码只通过子进程环境传递，不进入 argv。
        export REGENT_SSH_PASSWORD="$SERVER_PASSWORD"
        export SSH_ASKPASS="$DEPLOY_DIR/ssh-askpass.sh"
        export SSH_ASKPASS_REQUIRE=force
        export DISPLAY="${DISPLAY:-regent}"
        PASSWORD_SSH_OPTS=(
            -o BatchMode=no
            -o PreferredAuthentications=password,keyboard-interactive
            -o PubkeyAuthentication=no
        )
        SSH_COMMAND=(ssh "${PASSWORD_SSH_OPTS[@]}" "${SSH_OPTS[@]}" "$REMOTE_HOST")
        RSYNC_SSH="ssh -o BatchMode=no -o PreferredAuthentications=password,keyboard-interactive -o PubkeyAuthentication=no -p $SSH_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=30 -o NumberOfPasswordPrompts=1"
    fi
else
    SSH_COMMAND=(ssh -i "$SSH_KEY_PATH" "${SSH_OPTS[@]}" "$REMOTE_HOST")
    RSYNC_SSH="ssh -i $SSH_KEY_PATH -p $SSH_PORT -o StrictHostKeyChecking=no -o ConnectTimeout=30 -o NumberOfPasswordPrompts=1"
fi

log_info "前置检查：SSH 连接"
if ! PROBE_OUTPUT=$("${SSH_COMMAND[@]}" "echo ok" 2>&1); then
    log_error "SSH 连接失败：${REMOTE_HOST}（端口 ${SSH_PORT}）"
    printf '%s\n' "$PROBE_OUTPUT"
    exit 1
fi
log_info "1/7 构建中继"
(cd "$PROJECT_DIR" && mvn -q -pl regent-relay -am clean package -DskipTests)

log_info "2/7 构建前端"
(cd "$PROJECT_DIR/regent-web" && npm ci --registry=https://registry.npmmirror.com --strict-ssl=false && npm run build)

log_info "3/7 准备服务器目录"
"${SSH_COMMAND[@]}" \
    "mkdir -p $REMOTE_BASE/relay $REMOTE_BASE/frontend $REMOTE_BASE/script $REMOTE_BASE/logs $REMOTE_BASE/nginx"

log_info "4/7 上传中继与静态前端"
JAR=$(ls -t "$PROJECT_DIR"/regent-relay/target/regent-relay-*.jar | head -1)
"${SSH_COMMAND[@]}" "systemctl stop regent-relay 2>/dev/null || true"
rsync -az -e "$RSYNC_SSH" "$JAR" "$REMOTE_HOST:$REMOTE_BASE/relay/regent-relay.jar"
rsync -az --delete -e "$RSYNC_SSH" "$PROJECT_DIR/regent-web/dist/" "$REMOTE_HOST:$REMOTE_BASE/frontend/dist/"
rsync -az -e "$RSYNC_SSH" "$DEPLOY_DIR/scripts/" "$REMOTE_HOST:$REMOTE_BASE/script/"
rsync -az -e "$RSYNC_SSH" "$DEPLOY_DIR/nginx/" "$REMOTE_HOST:$REMOTE_BASE/nginx/"
"${SSH_COMMAND[@]}" "chmod +x $REMOTE_BASE/script/*.sh"

log_info "5/7 初始化服务器凭据"
# 已有部署沿用原密钥，保证重复部署不会把已经配好的连接器打挂
REMOTE_SECRET=$("${SSH_COMMAND[@]}" \
    "grep -m1 '^REGENT_RELAY_SHARED_SECRET=' $REMOTE_BASE/relay/relay.env 2>/dev/null | cut -d= -f2- || true")
if [ -z "$REMOTE_SECRET" ]; then
    # 密钥在本机生成后整份推上去：服务器不需要 openssl，也避免密钥回显到终端与构建日志
    RELAY_SECRET=$(openssl rand -hex 32)
    printf '%s\n' \
        'REGENT_RELAY_BIND=127.0.0.1' \
        'REGENT_RELAY_PORT=8788' \
        "REGENT_RELAY_SHARED_SECRET=$RELAY_SECRET" \
        'REGENT_RELAY_POLL_SECONDS=25' \
        'REGENT_RELAY_REQUEST_TIMEOUT_SECONDS=40' \
        'REGENT_RELAY_OFFLINE_SECONDS=45' \
        'REGENT_RELAY_QUEUE_CAPACITY=64' \
        'REGENT_RELAY_MAX_BODY_BYTES=33554432' \
        | "${SSH_COMMAND[@]}" "umask 077; cat > $REMOTE_BASE/relay/relay.env"
    log_info "已生成服务器中继密钥（不回显）"
else
    RELAY_SECRET="$REMOTE_SECRET"
    log_info "服务器已有 relay.env，沿用原密钥"
fi

log_info "6/7 安装并启动中继，再配对本机连接器"
sed "s#/root/app/regent-relay#$REMOTE_BASE#g" "$DEPLOY_DIR/systemd/regent-relay.service" \
    | "${SSH_COMMAND[@]}" "umask 022; cat > /etc/systemd/system/regent-relay.service"
"${SSH_COMMAND[@]}" "systemctl daemon-reload && systemctl enable regent-relay && systemctl restart regent-relay"
sleep 3
if "${SSH_COMMAND[@]}" "systemctl is-active --quiet regent-relay"; then
    log_info "公网中继已启动"
else
    log_error "公网中继未启动，执行 ssh $REMOTE_HOST 'journalctl -u regent-relay -n 80' 排查"
    exit 1
fi

if [ "${REGENT_SKIP_LOCAL_CONNECTOR:-}" = "1" ]; then
    log_warn "已跳过本机连接器配对（REGENT_SKIP_LOCAL_CONNECTOR=1）"
else
    # 远端中继必须先上线。反序会让连接器在部署中反复探活失败，最后只留下误导性的告警。
    log_info "配对本机中继连接器"
    if ! REGENT_SERVER_IP="$SERVER_IP" \
        REGENT_SSH_PORT="$SSH_PORT" \
        REGENT_SERVER_USER="$SERVER_USER" \
        REGENT_SERVER_PASSWORD="$SERVER_PASSWORD" \
        REGENT_REMOTE_BASE="$REMOTE_BASE" \
        REGENT_RELAY_SHARED_SECRET="$RELAY_SECRET" \
        bash "$PROJECT_DIR/deploy/local/connect-relay.sh"; then
        log_error "本机连接器未上线，公网 API 仍会返回 RELAY_OFFLINE"
        log_error "修复后单独运行 deploy/local/connect-relay.sh，再重试部署"
        exit 1
    fi
    log_info "本机中继连接器已上线"
fi

log_info "7/7 自动配置服务器 Nginx"
"${SSH_COMMAND[@]}" \
    "sed -i 's#/root/app/regent-relay#$REMOTE_BASE#g' $REMOTE_BASE/nginx/regent.conf.example"
NGINX_OUTPUT=$("${SSH_COMMAND[@]}" "REMOTE_BASE='$REMOTE_BASE' bash -s" <<'REMOTE_NGINX'
if ! command -v nginx >/dev/null 2>&1; then
    echo "[nginx] 服务器未安装 nginx，跳过自动配置"
    echo "[nginx] 可参考 $REMOTE_BASE/nginx/regent.conf.example 手工配置"
    exit 0
fi

CONF=$(nginx -V 2>&1 | tr ' ' '\n' | sed -n 's/^--conf-path=//p' | head -1)
if [ -z "$CONF" ] || [ ! -f "$CONF" ]; then
    CONF=""
    for candidate in /usr/local/nginx/conf/nginx.conf /etc/nginx/nginx.conf; do
        if [ -f "$candidate" ]; then CONF="$candidate"; break; fi
    done
fi
if [ -z "$CONF" ]; then
    echo "[nginx] 没找到 nginx.conf，跳过自动配置"
    exit 0
fi

if grep -q 'listen 18787' "$CONF"; then
    echo "[nginx] 已有 Regent 配置（listen 18787），跳过写入：$CONF"
    nginx -s reload >/dev/null 2>&1 || systemctl reload nginx >/dev/null 2>&1 || true
else
    BAK="$CONF.bak.$(date +%Y%m%d%H%M%S)-regent"
    cp "$CONF" "$BAK"
    CLOSE_LINE=$(awk '/^}[[:space:]]*$/{n=NR} END{print n}' "$CONF")
    if [ -z "$CLOSE_LINE" ]; then
        echo "[nginx] $CONF 里找不到 http 块收尾的 }，跳过自动写入"
        rm -f "$BAK"
    else
        head -n $((CLOSE_LINE-1)) "$CONF" > "$CONF.regent-new"
        awk 'length($0) > 0 { print "    " $0; next } { print }' \
            "$REMOTE_BASE/nginx/regent.conf.example" >> "$CONF.regent-new"
        tail -n +"$CLOSE_LINE" "$CONF" >> "$CONF.regent-new"
        mv "$CONF.regent-new" "$CONF"
        if nginx -t -c "$CONF" >/tmp/regent-nginx-test.log 2>&1; then
            if nginx -s reload >/dev/null 2>&1 || systemctl reload nginx >/dev/null 2>&1; then
                echo "[nginx] 已写入 Regent 配置并重载（备份：$BAK）"
            else
                echo "[nginx] 已写入配置但 reload 失败，请检查 nginx 进程（备份：$BAK）"
            fi
        else
            cp "$BAK" "$CONF"
            echo "[nginx] nginx -t 校验失败，已回滚 $CONF。报错如下："
            sed 's/^/  /' /tmp/regent-nginx-test.log
        fi
    fi
fi

echo "[nginx] 服务器本机自检 http://127.0.0.1:18787/health："
if curl -s -m 5 http://127.0.0.1:18787/health; then
    echo
else
    echo "[nginx] 自检失败：18787 暂不可达"
fi

if systemctl is-active --quiet firewalld 2>/dev/null && command -v firewall-cmd >/dev/null 2>&1; then
    if firewall-cmd --permanent --add-port=18787/tcp >/dev/null 2>&1 && firewall-cmd --reload >/dev/null 2>&1; then
        echo "[nginx] firewalld 已放行 18787/tcp"
    else
        echo "[nginx] firewalld 放行失败，请手工执行：firewall-cmd --permanent --add-port=18787/tcp && firewall-cmd --reload"
    fi
fi
REMOTE_NGINX
) || true

printf '%s\n' "$NGINX_OUTPUT"

cat <<TIP

部署完成。

  访问地址：http://$SERVER_IP:18787
  安装目录：$REMOTE_BASE
  （外网打不开就先检查云厂商安全组是否放行 18787/tcp）

  本机中继连接器已自动配对（~/.regent/relay-connector.env）。
  服务器只做转发，真正的 Regent 在本机，所以本机没起服务时公网只有静态页面：

     ./deploy/local/start-all.sh     # 后端 + 前端 + 中继连接器
     ./deploy/local/status.sh        # 看状态

  换服务器 / 换密钥 / 只想重配连接器：./deploy/local/connect-relay.sh

TIP

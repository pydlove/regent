#!/bin/bash
set -e

# ===================================================================
# Regent 一键部署脚本
# 运行环境：macOS / Linux（本地机器），需要 ssh / rsync / scp
# 目标服务器：Linux（CentOS / Ubuntu）
#
# 部署布局（与服务端 config 里的约定一致）：
#   /root/app/regent/backend/    regent-boot.jar + application-prod.yml + .env
#   /root/app/regent/frontend/   dist/（Nginx root 指到这里）
#   /root/app/regent/script/     服务端启停脚本
#   /root/app/regent/logs/       应用日志
# ===================================================================

# ============ 配置区（请按实际情况修改）============
SERVER_IP="${REGENT_SERVER_IP:-127.0.0.1}"
SERVER_USER="${REGENT_SERVER_USER:-root}"
SERVER_PASSWORD="${REGENT_SERVER_PASSWORD:-}"
SSH_KEY_PATH="${REGENT_SSH_KEY:-$HOME/.ssh/id_rsa}"
SSH_PORT="${REGENT_SSH_PORT:-22}"
# ==================================================

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$(dirname "$DEPLOY_DIR")")"
APP_NAME="regent"
REMOTE_BASE="/root/app/$APP_NAME"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
# printf 而不是 echo -e：脚本用 sh 调用时（POSIX 模式的 bash）echo 不认 -e，会打出「-e [INFO]」。
log_info()  { printf '%b\n' "${GREEN}[INFO]${NC} $*"; }
log_warn()  { printf '%b\n' "${YELLOW}[WARN]${NC} $*"; }
log_error() { printf '%b\n' "${RED}[ERROR]${NC} $*"; }

if [ "$SERVER_IP" = "127.0.0.1" ]; then
    log_error "还没有配置服务器地址。请改脚本顶部的 SERVER_IP，或先 export REGENT_SERVER_IP=..."
    exit 1
fi

SSH_OPTS=(-p "$SSH_PORT" -o StrictHostKeyChecking=no)
if [ -n "$SERVER_PASSWORD" ]; then
    command -v sshpass >/dev/null || { log_error "设置了密码但没有 sshpass，请安装或改用密钥登录"; exit 1; }
    SSH="sshpass -p $SERVER_PASSWORD ssh ${SSH_OPTS[*]} $SERVER_USER@$SERVER_IP"
    RSYNC_SSH="sshpass -p $SERVER_PASSWORD ssh ${SSH_OPTS[*]}"
else
    SSH="ssh -i $SSH_KEY_PATH ${SSH_OPTS[*]} $SERVER_USER@$SERVER_IP"
    RSYNC_SSH="ssh -i $SSH_KEY_PATH ${SSH_OPTS[*]}"
fi

log_info "1/6 构建后端"
(cd "$PROJECT_DIR" && mvn -q clean package -DskipTests)

log_info "2/6 构建前端"
(cd "$PROJECT_DIR/regent-web" && npm ci --registry=https://registry.npmmirror.com --strict-ssl=false && npm run build)

log_info "3/6 准备服务端目录"
$SSH "mkdir -p $REMOTE_BASE/backend $REMOTE_BASE/frontend $REMOTE_BASE/script $REMOTE_BASE/logs"

log_info "4/6 上传后端"
JAR=$(ls -t "$PROJECT_DIR"/regent-boot/target/regent-boot-*.jar | grep -v '\.original$' | head -1)
$SSH "systemctl stop regent 2>/dev/null || true"
rsync -az -e "$RSYNC_SSH" "$JAR" "$SERVER_USER@$SERVER_IP:$REMOTE_BASE/backend/regent-boot.jar"
$SSH "test -f $REMOTE_BASE/backend/application-prod.yml" \
    || log_warn "$REMOTE_BASE/backend/application-prod.yml 不存在，服务器上的库凭据需要你手工放一份"

log_info "5/6 上传前端与启停脚本"
rsync -az --delete -e "$RSYNC_SSH" "$PROJECT_DIR/regent-web/dist/" "$SERVER_USER@$SERVER_IP:$REMOTE_BASE/frontend/dist/"
rsync -az -e "$RSYNC_SSH" "$DEPLOY_DIR/scripts/" "$SERVER_USER@$SERVER_IP:$REMOTE_BASE/script/"
$SSH "chmod +x $REMOTE_BASE/script/*.sh"

log_info "6/6 安装 systemd 单元并启动"
rsync -az -e "$RSYNC_SSH" "$DEPLOY_DIR/systemd/regent.service" "$SERVER_USER@$SERVER_IP:/etc/systemd/system/regent.service"
$SSH "systemctl daemon-reload && systemctl enable regent && systemctl restart regent"
sleep 5
$SSH "systemctl is-active regent" >/dev/null && log_info "部署完成，服务已启动" || log_error "服务没有起来，看 journalctl -u regent -n 50"

cat <<TIP

后续要做的两件事：
  1. Nginx 的 root 指向 $REMOTE_BASE/frontend/dist，然后 nginx -s reload
  2. 确认 $REMOTE_BASE/backend/.env 里有 REGENT_DB_USERNAME / REGENT_DB_PASSWORD

TIP

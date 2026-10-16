#!/bin/bash
# 启动公网中继连接器。连接器只主动访问服务器，不开放任何本地监听端口。
source "$(dirname "$0")/_common.sh"

if relay_agent_loaded; then
    info "公网中继连接器由 launchd 托管，跳过（重启用 deploy/local/relay-agent.sh restart）"
    exit 0
fi

if [ -f "$RELAY_PID_FILE" ] && pid_alive "$(cat "$RELAY_PID_FILE")"; then
    info "公网中继连接器已在运行 (PID: $(cat "$RELAY_PID_FILE"))"
    exit 0
fi
rm -f "$RELAY_PID_FILE"

if [ ! -f "$RELAY_ENV_FILE" ]; then
    warn "未配置公网中继，跳过连接器"
    warn "复制示例到 $RELAY_ENV_FILE 并填入服务器地址与共享密钥"
    warn "示例：deploy/local/relay-connector.env.example"
    exit 0
fi

set -a
source "$RELAY_ENV_FILE"
set +a

: "${REGENT_RELAY_SERVER_URL:?relay-connector.env 缺少 REGENT_RELAY_SERVER_URL}"
: "${REGENT_RELAY_SHARED_SECRET:?relay-connector.env 缺少 REGENT_RELAY_SHARED_SECRET}"
export REGENT_RELAY_LOCAL_URL="${REGENT_RELAY_LOCAL_URL:-http://127.0.0.1:$BACKEND_PORT}"

JAR=$(ls -t "$PROJECT_DIR"/regent-relay/target/regent-relay-*.jar 2>/dev/null | head -1)
if [ -z "$JAR" ]; then
    warn "没有中继构建产物，先执行 Maven 打包"
    (cd "$PROJECT_DIR" && mvn -q -pl regent-relay -am package -DskipTests)
    JAR=$(ls -t "$PROJECT_DIR"/regent-relay/target/regent-relay-*.jar | head -1)
fi

echo "启动公网中继连接器..."
nohup java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
    -jar "$JAR" connector > "$RELAY_LOG" 2>&1 &
echo $! > "$RELAY_PID_FILE"

sleep 2
if pid_alive "$(cat "$RELAY_PID_FILE")"; then
    info "公网中继连接器启动成功 (PID: $(cat "$RELAY_PID_FILE"))"
    info "连接目标 $REGENT_RELAY_SERVER_URL"
else
    fail "公网中继连接器启动失败，最后 20 行日志："
    tail -20 "$RELAY_LOG"
    rm -f "$RELAY_PID_FILE"
    exit 1
fi

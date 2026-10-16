#!/bin/bash
# 把公网中继连接器交给 launchd 托管（macOS）：开机自启、崩溃自动重启、不挂在终端窗口上。
#
# 为什么需要它：服务器只做转发，真正干活的 Regent 在本机，连接器一断，手机打开公网地址
# 就只会看到「本地 Regent 未连接」。只靠 nohup 的话，关掉终端或重启机器就断了。
#
# 用法：relay-agent.sh install | uninstall | restart | status
export LC_CTYPE=UTF-8
source "$(dirname "$0")/_common.sh"

LABEL="$RELAY_AGENT_LABEL"
AGENT_DIR="$HOME/Library/LaunchAgents"
PLIST="$AGENT_DIR/$LABEL.plist"
WRAPPER="$RUNTIME_DIR/relay-connector.sh"
DOMAIN="gui/$(id -u)"

COMMAND="${1:-status}"

if [ "$(uname -s)" != "Darwin" ]; then
    fail "launchd 托管只在 macOS 可用；其他系统请自行用 systemd 托管 start-relay.sh"
    exit 1
fi

agent_loaded() { launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; }

server_url() { grep -m1 '^REGENT_RELAY_SERVER_URL=' "$RELAY_ENV_FILE" | cut -d= -f2-; }

# 连接器握手要一两秒，探活失败不代表配置错，给足重试
probe_connector() {
    local url health compact
    url=$(server_url)
    for _ in $(seq 1 15); do
        health=$(curl -fsS --max-time 5 "${url}/health" 2>/dev/null || true)
        compact=$(printf '%s' "$health" | tr -d ' ')
        case "$compact" in
            *'"connectorOnline":true'*) info "中继已连通：${url} → ${compact}"; return 0 ;;
        esac
        sleep 2
    done
    return 1
}

write_agent_files() {
    local jar java_bin
    jar=$(ls -t "$PROJECT_DIR"/regent-relay/target/regent-relay-*.jar 2>/dev/null | head -1)
    if [ -z "$jar" ]; then
        warn "没有中继构建产物，先执行 Maven 打包"
        (cd "$PROJECT_DIR" && mvn -q -pl regent-relay -am package -DskipTests)
        jar=$(ls -t "$PROJECT_DIR"/regent-relay/target/regent-relay-*.jar | head -1)
    fi
    java_bin=$(command -v java)
    if [ -z "$java_bin" ]; then
        fail "找不到 java，请确认 JDK 17 已安装且在 PATH 中"
        exit 1
    fi

    # 包一层 shell：配置仍只存 relay-connector.env 一处，密钥不进 plist
    umask 077
    cat > "$WRAPPER" <<EOF
#!/bin/bash
# 由 deploy/local/relay-agent.sh 生成，勿手改；重新 install 会覆盖
set -euo pipefail
set -a
source "$RELAY_ENV_FILE"
set +a
exec "$java_bin" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \\
    -jar "$jar" connector
EOF
    chmod 700 "$WRAPPER"

    mkdir -p "$AGENT_DIR"
    umask 022
    cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key><string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>/bin/bash</string>
        <string>$WRAPPER</string>
    </array>
    <key>RunAtLoad</key><true/>
    <key>KeepAlive</key><true/>
    <key>ThrottleInterval</key><integer>10</integer>
    <key>ProcessType</key><string>Background</string>
    <key>WorkingDirectory</key><string>$PROJECT_DIR</string>
    <key>StandardOutPath</key><string>$RELAY_LOG</string>
    <key>StandardErrorPath</key><string>$RELAY_LOG</string>
</dict>
</plist>
EOF
}

load_agent() {
    if agent_loaded; then launchctl bootout "$DOMAIN/$LABEL" >/dev/null 2>&1 || true; fi
    if ! launchctl bootstrap "$DOMAIN" "$PLIST" >/dev/null 2>&1; then
        # 老版本 macOS 没有 bootstrap，回退到 load -w
        launchctl load -w "$PLIST" >/dev/null 2>&1 || {
            fail "launchd 加载失败：$PLIST"
            exit 1
        }
    fi
}

case "$COMMAND" in
    install)
        if [ ! -f "$RELAY_ENV_FILE" ]; then
            fail "缺少 $RELAY_ENV_FILE；先运行 ./deploy/local/connect-relay.sh"
            exit 1
        fi
        # 先收掉 nohup 起的那个，避免同一条配置被连两份
        stop_by_pidfile "公网中继连接器" "$RELAY_PID_FILE"
        write_agent_files
        load_agent
        info "已安装 launchd 托管（$PLIST）"
        if probe_connector; then
            info "连接器常驻成功：开机自启、崩溃自动重启"
        else
            fail "连接器未上线，看日志：tail -50 $RELAY_LOG"
            exit 1
        fi
        ;;
    uninstall)
        if agent_loaded; then
            launchctl bootout "$DOMAIN/$LABEL" >/dev/null 2>&1 || launchctl unload -w "$PLIST" >/dev/null 2>&1 || true
            info "已停止 launchd 托管"
        else
            warn "托管未安装"
        fi
        rm -f "$PLIST" "$WRAPPER"
        info "已删除 $PLIST 与 $WRAPPER"
        ;;
    restart)
        if ! agent_loaded; then
            fail "托管未安装，先运行 ./deploy/local/relay-agent.sh install"
            exit 1
        fi
        launchctl kickstart -k "$DOMAIN/$LABEL" >/dev/null 2>&1 || {
            fail "重启失败：launchctl kickstart $DOMAIN/$LABEL"
            exit 1
        }
        if probe_connector; then info "连接器已重启"; else fail "重启后未上线，看日志：$RELAY_LOG"; exit 1; fi
        ;;
    status)
        if agent_loaded; then
            info "launchd 托管：已安装（PID $(launchctl print "$DOMAIN/$LABEL" 2>/dev/null | awk '/^\tpid = /{print $3}')）"
        else
            warn "launchd 托管：未安装"
        fi
        if [ -f "$RELAY_ENV_FILE" ]; then
            probe_connector || warn "连接器当前未上线"
        else
            warn "未配对中继：$RELAY_ENV_FILE 不存在"
        fi
        ;;
    *)
        fail "未知命令：$COMMAND（可用：install / uninstall / restart / status）"
        exit 1
        ;;
esac

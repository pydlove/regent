#!/bin/bash
# 把 Regent 设备代理交给 launchd 托管（macOS）：开机自启、崩溃重启、不依赖终端窗口。
#
# 为什么需要它：公网页面只能通过设备代理读取本机 Codex。代理只靠 npm run device-agent
# 启动时，终端一关就会显示“当前设备未连接 Regent 设备代理”。
#
# 用法：device-agent.sh install | uninstall | restart | status | ensure
export LC_CTYPE=UTF-8
source "$(dirname "$0")/_common.sh"

LABEL="$DEVICE_AGENT_LABEL"
AGENT_DIR="$HOME/Library/LaunchAgents"
PLIST="$AGENT_DIR/$LABEL.plist"
WRAPPER="$RUNTIME_DIR/device-agent-wrapper.sh"
DOMAIN="gui/$(id -u)"

COMMAND="${1:-status}"

if [ "$(uname -s)" != "Darwin" ]; then
    fail "launchd 托管只在 macOS 可用；其他系统请自行托管 npm run device-agent"
    exit 1
fi

agent_loaded() { launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; }

agent_json() {
    curl -fsS --max-time 10 "http://127.0.0.1:$DEVICE_AGENT_PORT/status" 2>/dev/null || true
}

agent_online() {
    [ -n "$(agent_json)" ]
}

agent_paired() {
    case "$(agent_json | tr -d '[:space:]')" in
        *'"paired":true'*) return 0 ;;
        *) return 1 ;;
    esac
}

wait_agent() {
    local i=0
    while [ "$i" -lt 30 ]; do
        if agent_online; then return 0; fi
        sleep 0.5
        i=$((i + 1))
    done
    return 1
}

write_agent_files() {
    local node_bin
    node_bin=$(command -v node || true)
    if [ -z "$node_bin" ]; then
        fail "找不到 node，请确认 Node.js 20+ 已安装且在 PATH 中"
        exit 1
    fi
    if [ ! -f "$PROJECT_DIR/regent-desktop/scripts/start-device-agent.cjs" ]; then
        fail "找不到设备代理启动脚本：$PROJECT_DIR/regent-desktop/scripts/start-device-agent.cjs"
        exit 1
    fi

    umask 077
    cat > "$WRAPPER" <<EOF
#!/bin/bash
# 由 deploy/local/device-agent.sh 生成，勿手改；重新 install 会覆盖
set -euo pipefail
export PATH="$(dirname "$node_bin"):\$HOME/.local/bin:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin"
cd "$PROJECT_DIR/regent-desktop"
exec "$node_bin" --use-system-ca "$PROJECT_DIR/regent-desktop/scripts/start-device-agent.cjs"
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
    <key>LimitLoadToSessionType</key><string>Aqua</string>
    <key>WorkingDirectory</key><string>$PROJECT_DIR/regent-desktop</string>
    <key>StandardOutPath</key><string>$DEVICE_AGENT_LOG</string>
    <key>StandardErrorPath</key><string>$DEVICE_AGENT_LOG</string>
</dict>
</plist>
EOF
}

unload_agent() {
    if agent_loaded; then
        launchctl bootout "$DOMAIN/$LABEL" >/dev/null 2>&1 || true
    fi
    launchctl remove "$LABEL" >/dev/null 2>&1 || true
}

load_agent() {
    unload_agent
    if ! launchctl bootstrap "$DOMAIN" "$PLIST" >/dev/null 2>&1; then
        launchctl load -w "$PLIST" >/dev/null 2>&1 || {
            fail "launchd 加载失败：$PLIST"
            exit 1
        }
    fi
}

report_agent_state() {
    if agent_paired; then
        info "设备代理常驻成功：已连接服务器，浏览器可直接检测本机 Codex"
    else
        warn "设备代理已常驻，但尚未配对；在浏览器设置页点击“重新检测”完成一次配对"
    fi
}

case "$COMMAND" in
    install)
        local_pid=$(listen_pid "$DEVICE_AGENT_PORT")
        if [ -n "$local_pid" ] && ! agent_loaded; then
            fail "端口 $DEVICE_AGENT_PORT 已有非托管设备代理在运行 (PID: $local_pid)"
            fail "先结束该进程，再执行 deploy/local/device-agent.sh install"
            exit 1
        fi
        write_agent_files
        load_agent
        if wait_agent; then
            report_agent_state
        else
            fail "设备代理未就绪，看日志：tail -50 $DEVICE_AGENT_LOG"
            exit 1
        fi
        ;;
    uninstall)
        unload_agent
        info "已停止 launchd 托管"
        rm -f "$PLIST" "$WRAPPER"
        info "已删除 $PLIST 与 $WRAPPER"
        ;;
    restart)
        if ! agent_loaded; then
            fail "托管未安装，先运行 ./deploy/local/device-agent.sh install"
            exit 1
        fi
        launchctl kickstart -k "$DOMAIN/$LABEL" >/dev/null 2>&1 || {
            fail "重启失败：launchctl kickstart $DOMAIN/$LABEL"
            exit 1
        }
        if wait_agent; then
            report_agent_state
        else
            fail "重启后未就绪，看日志：tail -50 $DEVICE_AGENT_LOG"
            exit 1
        fi
        ;;
    ensure)
        if agent_loaded && agent_online; then
            report_agent_state
            exit 0
        fi
        "$0" install
        ;;
    status)
        agent_state=$(agent_json)
        if agent_loaded; then
            info "launchd 托管：已安装（PID $(launchctl print "$DOMAIN/$LABEL" 2>/dev/null | awk '/^\tpid = /{print $3}')）"
        else
            warn "launchd 托管：未安装"
        fi
        if [ -n "$agent_state" ]; then
            if printf '%s' "$agent_state" | tr -d '[:space:]' | grep -Fq '"paired":true'; then
                info "设备代理：在线，已配对"
            else
                warn "设备代理：在线，等待浏览器配对"
            fi
        else
            warn "设备代理：未监听 $DEVICE_AGENT_PORT"
        fi
        ;;
    *)
        fail "未知命令：$COMMAND（可用：install / uninstall / restart / status / ensure）"
        exit 1
        ;;
esac

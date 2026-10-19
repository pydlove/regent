#!/bin/bash
# 把本机后端交给 launchd 托管（macOS）：开机自启、崩溃重启，且每次启动都从稳定副本运行。
#
# 为什么需要它：Spring Boot 的 fat jar 不能在 target/ 里长期运行。mvn clean 会删掉
# JVM 正在使用的文件，进程还在，但后面的类加载和 ServiceLoader 会随机失败。
# 典型表现是安装好的桌面端能打开，注册时却报「服务端未预期错误」。
#
# 用法：backend-agent.sh install | uninstall | restart | status
export LC_CTYPE=UTF-8
source "$(dirname "$0")/_common.sh"

LABEL="$BACKEND_AGENT_LABEL"
AGENT_DIR="$HOME/Library/LaunchAgents"
PLIST="$AGENT_DIR/$LABEL.plist"
WRAPPER="$RUNTIME_DIR/backend.sh"
DOMAIN="gui/$(id -u)"

COMMAND="${1:-status}"

if [ "$(uname -s)" != "Darwin" ]; then
    fail "launchd 托管只在 macOS 可用；Windows 请用计划任务或服务方式托管 start-backend.sh"
    exit 1
fi

agent_loaded() { launchctl print "$DOMAIN/$LABEL" >/dev/null 2>&1; }

latest_jar() {
    local latest="" candidate
    for candidate in "$PROJECT_DIR"/regent-boot/target/regent-boot-*.jar; do
        [ -f "$candidate" ] || continue
        case "$candidate" in *.original) continue ;; esac
        if [ -z "$latest" ] || [ "$candidate" -nt "$latest" ]; then
            latest="$candidate"
        fi
    done
    printf '%s' "$latest"
}

write_agent_files() {
    local jar java_bin
    jar=$(latest_jar)
    if [ -z "$jar" ] && [ ! -f "$BACKEND_RUNTIME_JAR" ]; then
        warn "没有后端构建产物，先执行 Maven 打包"
        (cd "$PROJECT_DIR" && mvn -q -pl regent-boot -am package -DskipTests)
        jar=$(latest_jar)
    fi
    if [ -z "$jar" ] && [ ! -f "$BACKEND_RUNTIME_JAR" ]; then
        fail "找不到可运行的后端 jar"
        exit 1
    fi
    java_bin=$(command -v java)
    if [ -z "$java_bin" ]; then
        fail "找不到 java，请确认 JDK 17 已安装且在 PATH 中"
        exit 1
    fi

    umask 077
    cat > "$WRAPPER" <<EOF
#!/bin/bash
# 由 deploy/local/backend-agent.sh 生成，勿手改；重新 install 会覆盖
set -euo pipefail
cd "$PROJECT_DIR"
echo \$\$ > "$BACKEND_PID_FILE"

# 每次被 launchd 拉起时同步最新构建产物。构建失败或还没构建时保留上一次的稳定副本，
# 不会因为 target/ 被清空而启动一个空壳。
latest=""
for candidate in "$PROJECT_DIR"/regent-boot/target/regent-boot-*.jar; do
    [ -f "\$candidate" ] || continue
    case "\$candidate" in *.original) continue ;; esac
    if [ -z "\$latest" ] || [ "\$candidate" -nt "\$latest" ]; then
        latest="\$candidate"
    fi
done
if [ -n "\$latest" ]; then
    cp "\$latest" "$BACKEND_RUNTIME_JAR.tmp"
    mv -f "$BACKEND_RUNTIME_JAR.tmp" "$BACKEND_RUNTIME_JAR"
fi
if [ ! -f "$BACKEND_RUNTIME_JAR" ]; then
    echo "找不到运行时 jar：$BACKEND_RUNTIME_JAR" >&2
    exit 1
fi

exec "$java_bin" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \\
    -jar "$BACKEND_RUNTIME_JAR"
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
    <key>StandardOutPath</key><string>$BACKEND_LOG</string>
    <key>StandardErrorPath</key><string>$BACKEND_LOG</string>
</dict>
</plist>
EOF
}

unload_agent() {
    if agent_loaded; then
        launchctl bootout "$DOMAIN/$LABEL" >/dev/null 2>&1 || true
    fi
    # 旧会话可能用 launchctl submit 注册过同名临时任务，bootout 收不掉时要显式 remove。
    launchctl remove "$LABEL" >/dev/null 2>&1 || true
}

load_agent() {
    unload_agent
    if ! launchctl bootstrap "$DOMAIN" "$PLIST" >/dev/null 2>&1; then
        # 老版本 macOS 没有 bootstrap，回退到 load -w
        launchctl load -w "$PLIST" >/dev/null 2>&1 || {
            fail "launchd 加载失败：$PLIST"
            exit 1
        }
    fi
}

probe_backend() {
    wait_http "http://127.0.0.1:$BACKEND_PORT/actuator/health" 180
}

case "$COMMAND" in
    install)
        stop_by_pidfile "后端" "$BACKEND_PID_FILE"
        write_agent_files
        load_agent
        if probe_backend; then
            info "已安装 launchd 托管（$PLIST）"
            info "后端常驻成功：开机自启、崩溃自动重启、运行包 $BACKEND_RUNTIME_JAR"
        else
            fail "后端未就绪，看日志：tail -50 $BACKEND_LOG"
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
            fail "托管未安装，先运行 ./deploy/local/backend-agent.sh install"
            exit 1
        fi
        launchctl kickstart -k "$DOMAIN/$LABEL" >/dev/null 2>&1 || {
            fail "重启失败：launchctl kickstart $DOMAIN/$LABEL"
            exit 1
        }
        if probe_backend; then info "后端已重启"; else fail "重启后未就绪，看日志：$BACKEND_LOG"; exit 1; fi
        ;;
    status)
        if agent_loaded; then
            info "launchd 托管：已安装（PID $(launchctl print "$DOMAIN/$LABEL" 2>/dev/null | awk '/^\tpid = /{print $3}')）"
        else
            warn "launchd 托管：未安装"
        fi
        if probe_backend; then info "后端健康检查通过"; else warn "后端当前未就绪"; fi
        ;;
    *)
        fail "未知命令：$COMMAND（可用：install / uninstall / restart / status）"
        exit 1
        ;;
esac

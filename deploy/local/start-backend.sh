#!/bin/bash
# 启动后端（Spring Boot）。已在运行则直接返回。
source "$(dirname "$0")/_common.sh"

EXISTING=$(listen_pid $BACKEND_PORT)
if [ -n "$EXISTING" ]; then
    # 变量一律用 ${} 包住：bash 在非 UTF-8 locale 下会把紧跟其后的汉字字节当成变量名的一部分，
    # 报成「EXISTING?: unbound variable」，与真实原因隔着十万八千里。
    info "后端已在运行 (PID: ${EXISTING}，端口 $BACKEND_PORT)"
    exit 0
fi

# launchd 托管时不能另起一个 nohup 进程，否则会和 KeepAlive 抢同一个端口。
# 托管脚本每次启动都会把最新构建产物同步到稳定目录，这里只需要唤醒它。
if backend_agent_loaded; then
    info "后端由 launchd 托管，正在启动..."
    launchctl kickstart -k "gui/$(id -u)/$BACKEND_AGENT_LABEL" >/dev/null 2>&1 || {
        fail "launchd 启动失败：launchctl kickstart gui/$(id -u)/$BACKEND_AGENT_LABEL"
        exit 1
    }
    if wait_http "http://127.0.0.1:$BACKEND_PORT/actuator/health" 180; then
        info "后端启动成功 (PID: $(cat "$BACKEND_PID_FILE"))"
        exit 0
    fi
    fail "后端 90 秒内没有就绪，最后 20 行日志："
    tail -20 "$BACKEND_LOG"
    exit 1
fi

JAR=$(ls -t "$PROJECT_DIR"/regent-boot/target/regent-boot-*.jar 2>/dev/null | grep -v '\.original$' | head -1)
if [ -z "$JAR" ]; then
    warn "没有构建产物，先执行 mvn -q clean package -DskipTests"
    (cd "$PROJECT_DIR" && mvn -q clean package -DskipTests)
    JAR=$(ls -t "$PROJECT_DIR"/regent-boot/target/regent-boot-*.jar | grep -v '\.original$' | head -1)
fi

# 数据库凭据在 application-local.yml 里，profile 默认就是 local。
# 若要从环境变量覆盖：REGENT_DB_USERNAME / REGENT_DB_PASSWORD
# -Dfile.encoding=UTF-8 不是可选项：JVM 的默认字符集跟着运行环境走（LANG 没设时是 US-ASCII），
# 它决定日志文件按什么编码写、也决定交给子进程的参数按什么编码编。后者会把中文指令打成问号
# （适配器已改走 stdin 绕开 argv），但前者同样致命 —— 日志里一排问号是没法排错的。
export REGENT_RELAY_INSTALL_SCRIPT="${REGENT_RELAY_INSTALL_SCRIPT:-$PROJECT_DIR/deploy/remote/deploy.sh}"

# 复制到运行时目录再启动。直接运行 target/ 下的 fat jar 时，下一次 mvn clean 会把 JVM
# 正在使用的文件删掉：进程还在，但后续类加载、资源读取和 ServiceLoader 会抛
# NoSuchFileException，表现为登录、发验证码等接口突然报「服务端未预期错误」。
# 先写临时文件再 mv，避免中途失败留下半个 jar 被下一次启动当成可用产物。
cp "$JAR" "$BACKEND_RUNTIME_JAR.tmp"
mv -f "$BACKEND_RUNTIME_JAR.tmp" "$BACKEND_RUNTIME_JAR"

echo "启动后端（${BACKEND_RUNTIME_JAR}，来源 ${JAR}）..."
nohup java -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
    -jar "$BACKEND_RUNTIME_JAR" > "$BACKEND_LOG" 2>&1 &
echo $! > "$BACKEND_PID_FILE"

if wait_http "http://127.0.0.1:$BACKEND_PORT/actuator/health" 180; then
    info "后端启动成功 (PID: $(cat "$BACKEND_PID_FILE"))"
    info "健康检查 http://127.0.0.1:$BACKEND_PORT/actuator/health"
else
    fail "后端 90 秒内没有就绪，最后 20 行日志："
    tail -20 "$BACKEND_LOG"
    rm -f "$BACKEND_PID_FILE"
    exit 1
fi

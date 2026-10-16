#!/bin/bash
# 启动前端（Vite 开发服务器）。已在运行则直接返回。
source "$(dirname "$0")/_common.sh"

EXISTING=$(listen_pid $FRONTEND_PORT)
if [ -n "$EXISTING" ]; then
    info "前端已在运行 (PID: ${EXISTING}，端口 $FRONTEND_PORT)"
    exit 0
fi

cd "$PROJECT_DIR/regent-web"

if [ ! -d node_modules ]; then
    warn "还没有装依赖，本机 npm 走自签证书代理，必须带这两个参数"
    npm install --registry=https://registry.npmmirror.com --strict-ssl=false
fi

# 直接起 vite，不走 `npm run dev`：npm 会多套一层壳进程，pid 文件里记的是壳的号，
# 停服务时杀掉壳、真正监听端口的 node 还活着，只能靠 stop-all 的端口兜底补救。
VITE_BIN="$PROJECT_DIR/regent-web/node_modules/.bin/vite"
if [ ! -x "$VITE_BIN" ]; then
    fail "找不到 ${VITE_BIN}，先在 regent-web 下 npm install"
    exit 1
fi

echo "启动前端（vite dev）..."
nohup "$VITE_BIN" > "$FRONTEND_LOG" 2>&1 &
echo $! > "$FRONTEND_PID_FILE"

if wait_http "http://127.0.0.1:$FRONTEND_PORT/" 40; then
    info "前端启动成功 (PID: $(cat "$FRONTEND_PID_FILE"))"
    info "打开 http://127.0.0.1:$FRONTEND_PORT/"
else
    fail "前端 20 秒内没有就绪，最后 20 行日志："
    tail -20 "$FRONTEND_LOG"
    rm -f "$FRONTEND_PID_FILE"
    exit 1
fi

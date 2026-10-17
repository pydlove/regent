#!/bin/bash
source "$(dirname "$0")/_common.sh"

# 先构建再停服务：构建失败时旧实例仍在运行，不会把本地环境留在“已停止且无法启动”的状态。
# start-backend.sh 只在 jar 不存在时构建；若这里省略构建，改完后端源码后重启仍会运行旧 jar。
echo "构建后端..."
(cd "$PROJECT_DIR" && mvn -q -pl regent-boot -am clean package -DskipTests)

"$SCRIPT_DIR/stop-all.sh"
echo
sleep 2
"$SCRIPT_DIR/start-all.sh"

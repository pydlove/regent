#!/bin/bash

if [ -z "${BASH_VERSION:-}" ]; then
    exec /bin/bash "$0" "$@"
fi

set -Eeuo pipefail

# Regent 公网中继部署入口（双模式）。
#
# 本地模式：显式传入 -H/--host（或 REGENT_SERVER_IP），委托 deploy-local.sh
# 完成构建、上传、服务重启和 Nginx 配置。
#
# 远端模式：平台执行时不传 -H，并注入 REGENT_DEPLOY_DIRECTORY。
# 脚本直接在当前服务器处理已暂存的 JAR/前端包，不重复建立 SSH 连接。

deploy_dir="$(cd "$(dirname "$0")" && pwd)"
local_script="$deploy_dir/deploy-local.sh"

HOST=""
if [ -n "${REGENT_SERVER_IP:-}" ]; then
    HOST="$REGENT_SERVER_IP"
fi

args=("$@")
show_help=false
for ((index = 0; index < ${#args[@]}; index++)); do
    case "${args[$index]}" in
        -H|--host)
            if [ $((index + 1)) -ge ${#args[@]} ] || [ -z "${args[$((index + 1))]:-}" ]; then
                printf '错误：%s 缺少服务器地址\n' "${args[$index]}" >&2
                exit 1
            fi
            HOST="${args[$((index + 1))]}"
            index=$((index + 1))
            ;;
        -h|--help)
            show_help=true
            ;;
    esac
done

run_local() {
    if [ ! -x "$local_script" ]; then
        printf '错误：本地部署脚本不存在或不可执行：%s\n' "$local_script" >&2
        exit 1
    fi
    exec "$local_script" "$@"
}

run_remote() {
    : "${REGENT_DEPLOY_DIRECTORY:?缺少 REGENT_DEPLOY_DIRECTORY；本地发布请显式传入 -H/--host}"

    local input_dir="$REGENT_DEPLOY_DIRECTORY"
    local app_dir="$input_dir"
    local relay_dir="$app_dir/relay"
    local frontend_dir="$app_dir/frontend"
    local service_name="${REGENT_DEPLOY_SERVICE:-regent-relay}"
    local owner_group="${REGENT_DEPLOY_OWNER_GROUP:-root:root}"
    local timestamp
    local sudo_cmd=()

    if [ ! -d "$app_dir/relay" ] && [ -d "$app_dir/regent-relay" ]; then
        app_dir="$input_dir/regent-relay"
        relay_dir="$app_dir/relay"
        frontend_dir="$app_dir/frontend"
    fi

    timestamp="$(date +%Y%m%d_%H%M%S)"
    if [ "$(id -u)" -ne 0 ]; then
        sudo_cmd=(sudo -n)
    fi

    cd "$app_dir"
    printf '[deploy] 模式=远端 input=%s directory=%s service=%s\n' \
        "$input_dir" "$app_dir" "$service_name"

    "${sudo_cmd[@]}" mkdir -p "$relay_dir" "$frontend_dir"

    local staged_jar=""
    for candidate in \
        "$input_dir/relay/regent-relay.jar.new" \
        "$input_dir/regent-relay.jar.new" \
        "$relay_dir/regent-relay.jar.new"; do
        if [ -f "$candidate" ]; then
            staged_jar="$candidate"
            break
        fi
    done

    if [ -n "$staged_jar" ]; then
        printf '[1/3] 替换中继 JAR\n'
        if [ -f "$relay_dir/regent-relay.jar" ]; then
            "${sudo_cmd[@]}" cp -p \
                "$relay_dir/regent-relay.jar" \
                "$relay_dir/regent-relay.jar.bak.$timestamp"
        fi
        "${sudo_cmd[@]}" mv -f "$staged_jar" "$relay_dir/regent-relay.jar"
    elif [ -f "$relay_dir/regent-relay.jar" ]; then
        printf '[1/3] 未发现 regent-relay.jar.new，沿用线上 JAR\n'
    else
        printf '错误：既没有 regent-relay.jar.new，也没有 regent-relay.jar\n' >&2
        exit 1
    fi

    local frontend_archive=""
    for candidate in \
        "$input_dir/frontend.tar.gz" \
        "$app_dir/frontend.tar.gz"; do
        if [ -f "$candidate" ]; then
            frontend_archive="$candidate"
            break
        fi
    done

    if [ -n "$frontend_archive" ]; then
        printf '[2/3] 更新静态前端\n'
        if [ -d "$frontend_dir" ]; then
            "${sudo_cmd[@]}" cp -a \
                "$frontend_dir" \
                "$app_dir/frontend.bak.$timestamp"
        fi
        "${sudo_cmd[@]}" tar -xzf "$frontend_archive" -C "$app_dir"
        "${sudo_cmd[@]}" rm -f "$frontend_archive"
    else
        printf '[2/3] 未发现 frontend.tar.gz，沿用线上前端\n'
    fi

    "${sudo_cmd[@]}" chown -R "$owner_group" "$relay_dir" "$frontend_dir"
    "${sudo_cmd[@]}" chmod 644 "$relay_dir/regent-relay.jar"
    "${sudo_cmd[@]}" find "$frontend_dir" -type d -exec chmod 755 {} +
    "${sudo_cmd[@]}" find "$frontend_dir" -type f -exec chmod 644 {} +

    printf '[3/3] 重启 %s\n' "$service_name"
    "${sudo_cmd[@]}" systemctl restart "$service_name"
    service_ready=false
    for _ in {1..15}; do
        if "${sudo_cmd[@]}" systemctl is-active --quiet "$service_name"; then
            service_ready=true
            break
        fi
        sleep 1
    done
    if [ "$service_ready" != true ]; then
        printf '错误：%s 重启后未进入 active 状态\n' "$service_name" >&2
        "${sudo_cmd[@]}" systemctl --no-pager --lines=20 status "$service_name" || true
        exit 1
    fi
    printf '=== 远端部署完成：%s ===\n' "$service_name"
}

if [ "$show_help" = true ]; then
    run_local --help
fi

if [ -n "$HOST" ]; then
    run_local "$@"
else
    run_remote
fi

export type DeploymentScriptVariable = {
  name: string
  description: string
}

export const DEPLOYMENT_SCRIPT_VARIABLES: DeploymentScriptVariable[] = [
  { name: 'REGENT_DEPLOY_RUN_ID', description: '本次执行记录 ID，便于日志与服务端操作对账' },
  { name: 'REGENT_DEPLOY_PROJECT_ID', description: '部署归属项目 ID' },
  { name: 'REGENT_DEPLOY_HOST', description: '当前部署的服务器地址' },
  { name: 'REGENT_DEPLOY_PORT', description: 'SSH 端口' },
  { name: 'REGENT_DEPLOY_USER', description: 'SSH 登录用户' },
  { name: 'REGENT_DEPLOY_AUTH_TYPE', description: '认证方式，值为 KEY 或 PASSWORD' },
  { name: 'REGENT_DEPLOY_DIRECTORY', description: '脚本执行时所在的远程目录' },
  { name: 'REGENT_DEPLOY_SCRIPT', description: '部署脚本文件名' },
]

export const DEPLOYMENT_SCRIPT_RULES = [
  '第一行使用 bash shebang，并启用 set -Eeuo pipefail；缺少失败退出或未定义变量保护会被拒绝。',
  '平台通过 SSH 连接服务器，并先 cd 到部署配置的远程目录；脚本里不要写死 cd /...，需要读取路径时使用 $REGENT_DEPLOY_DIRECTORY。',
  '不要在脚本里重复接收 IP、端口、用户名，也不要再执行 ssh / scp / sftp；连接信息由服务器资源统一提供。',
  '脚本终端是非交互式的。不要等待 read -p 或 sudo 输入；需要 sudo 时必须使用 sudo -n，并提前配置 NOPASSWD。',
  '已有 deploy / script 脚本可作为服务名、构建产物、备份和重启步骤的业务参考；连接信息与远程路径仍必须改写为平台契约。',
  '退出码 0 表示成功，非 0 表示失败；stdout 与 stderr 会合并写入本次部署日志。',
  'SSH 密码只用于建立连接，不会传给远端脚本。业务凭据请从服务器的受限配置或密钥管理中读取。',
] as const

export const DEPLOYMENT_SCRIPT_TEMPLATE = `#!/usr/bin/env bash
set -Eeuo pipefail

readonly target_host="$REGENT_DEPLOY_HOST"
readonly target_user="$REGENT_DEPLOY_USER"
readonly app_dir="$REGENT_DEPLOY_DIRECTORY"

echo "[deploy] run=$REGENT_DEPLOY_RUN_ID"
echo "[deploy] project=$REGENT_DEPLOY_PROJECT_ID"
echo "[deploy] $target_user@$target_host:$REGENT_DEPLOY_PORT"
echo "[deploy] script=$REGENT_DEPLOY_SCRIPT auth=$REGENT_DEPLOY_AUTH_TYPE"
echo "[deploy] workdir=$app_dir"

# 在这里替换构建产物、调整权限并重启服务。
# 任一步失败都应直接返回非零退出码。
`

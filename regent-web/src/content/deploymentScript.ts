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
  '平台通过 SSH 连接服务器，并在远程目录下执行 bash -s；不要在脚本里重复接收 IP、端口或用户名。',
  '脚本终端是非交互式的。不要等待 read、密码提示或 sudo 输入；需要 sudo 时请配置 NOPASSWD。',
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

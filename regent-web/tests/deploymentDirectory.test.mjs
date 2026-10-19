import assert from 'node:assert/strict'
import test from 'node:test'

const { inferDeploymentDirectory } = await import('../src/utils/deploymentDirectory.ts')

test('优先识别 REMOTE_DIR，并忽略配置和备份目录', () => {
  const script = `#!/usr/bin/env bash
set -Eeuo pipefail
REMOTE_DIR="/usr/setup/sec_api/web/admin/lib"
REMOTE_CONF_DIR="/usr/setup/sec_api/web/admin/conf"
REMOTE_BACKUP_DIR="\${REMOTE_DIR}.bak"
`

  assert.equal(
    inferDeploymentDirectory(script),
    '/usr/setup/sec_api/web/admin/lib',
  )
})

test('展开脚本内已定义的目录变量', () => {
  const script = `#!/usr/bin/env bash
APP_ROOT=/opt/regent
APP_DIR="\${APP_ROOT}/current"
`

  assert.equal(inferDeploymentDirectory(script), '/opt/regent/current')
})

test('没有目录变量时从 cd 绝对路径推断', () => {
  const script = `#!/usr/bin/env bash
set -Eeuo pipefail
cd /srv/regent/current
`

  assert.equal(inferDeploymentDirectory(script), '/srv/regent/current')
})

test('不猜测平台注入目录、未定义变量和临时目录', () => {
  assert.equal(
    inferDeploymentDirectory(`#!/usr/bin/env bash
app_dir="$REGENT_DEPLOY_DIRECTORY"
cd "$UNKNOWN_APP_DIR"
`),
    null,
  )
  assert.equal(
    inferDeploymentDirectory(`#!/usr/bin/env bash
DEPLOY_DIR=/tmp/regent
`),
    null,
  )
})

test('忽略 heredoc 中的目录示例', () => {
  const script = `#!/usr/bin/env bash
cat <<'EXAMPLE'
REMOTE_DIR=/wrong/path
EXAMPLE
REMOTE_DIR='/srv/regent'
`

  assert.equal(inferDeploymentDirectory(script), '/srv/regent')
})

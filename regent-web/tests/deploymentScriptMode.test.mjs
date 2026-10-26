import assert from 'node:assert/strict'
import test from 'node:test'

const { isProjectReleaseScript } = await import('../src/utils/deploymentScriptMode.ts')

test('传统 deploy-local 脚本识别为项目发布脚本', () => {
  assert.equal(
    isProjectReleaseScript(`
      ./deploy-local.sh
      target="\${REGENT_SERVER_IP}"
    `),
    true,
  )
})

test('双模式脚本通过 run_local/run_remote 和 -H 识别为项目发布脚本', () => {
  assert.equal(
    isProjectReleaseScript(`
      HOST=""
      run_local() {
        ssh "$HOST" true
      }
      run_remote() {
        echo remote
      }
      # 本地发布：script.sh -H/--host HOST
    `),
    true,
  )
})

test('远端单模式脚本不会被误判', () => {
  assert.equal(
    isProjectReleaseScript(`
      HOST=""
      run_remote() {
        echo remote
      }
      run_remote
    `),
    false,
  )
})

test('仅在注释中提到双模式函数不会触发识别', () => {
  assert.equal(
    isProjectReleaseScript(`
      # 如需兼容本地发布，可新增 run_local() 和 run_remote()
      # 参数 -H/--host
      echo remote
    `),
    false,
  )
})

import assert from 'node:assert/strict'
import test from 'node:test'

const {
  DEPLOYMENT_SCRIPT_RULES,
  DEPLOYMENT_SCRIPT_TEMPLATE,
  DEPLOYMENT_SCRIPT_VARIABLES,
} = await import('../src/content/deploymentScript.ts')

test('部署脚本变量名唯一且模板引用全部连接上下文', () => {
  const names = DEPLOYMENT_SCRIPT_VARIABLES.map((item) => item.name)
  assert.equal(new Set(names).size, names.length)
  assert.deepEqual(names, [
    'REGENT_DEPLOY_RUN_ID',
    'REGENT_DEPLOY_PROJECT_ID',
    'REGENT_DEPLOY_HOST',
    'REGENT_DEPLOY_PORT',
    'REGENT_DEPLOY_USER',
    'REGENT_DEPLOY_AUTH_TYPE',
    'REGENT_DEPLOY_DIRECTORY',
    'REGENT_DEPLOY_SCRIPT',
  ])
  for (const name of names) {
    assert.match(DEPLOYMENT_SCRIPT_TEMPLATE, new RegExp(`\\$\\{?${name}`))
  }
})

test('部署规范明确密码不下发且脚本必须非交互执行', () => {
  const rules = DEPLOYMENT_SCRIPT_RULES.join('\n')
  assert.match(rules, /SSH 密码.*不会传给远端脚本/)
  assert.match(rules, /非交互式/)
  assert.doesNotMatch(DEPLOYMENT_SCRIPT_TEMPLATE, /REGENT_DEPLOY_PASSWORD/)
})

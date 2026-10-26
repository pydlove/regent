import assert from 'node:assert/strict'
import test from 'node:test'

const { resolveProjectScriptPath } = await import('../src/utils/projectScriptPath.ts')

test('macOS 上传脚本按项目目录计算相对路径', () => {
  assert.equal(
    resolveProjectScriptPath(
      'deploy_testplatform.sh',
      '/Users/panyong/shanjie/testplatform/scripts/deploy_testplatform.sh',
      '/Users/panyong/shanjie/testplatform/',
    ),
    'scripts/deploy_testplatform.sh',
  )
})

test('Windows 路径忽略盘符和目录大小写', () => {
  assert.equal(
    resolveProjectScriptPath(
      'deploy.ps1',
      'C:\\Work\\Project\\deploy\\deploy.ps1',
      'c:\\work\\project',
    ),
    'deploy/deploy.ps1',
  )
})

test('脚本不在项目目录或没有绝对路径时退回文件名', () => {
  assert.equal(
    resolveProjectScriptPath(
      'deploy_testplatform.sh',
      '/Users/panyong/Downloads/deploy_testplatform.sh',
      '/Users/panyong/shanjie/testplatform',
    ),
    'deploy_testplatform.sh',
  )
  assert.equal(
    resolveProjectScriptPath('deploy_testplatform.sh', null, '/tmp/project'),
    'deploy_testplatform.sh',
  )
})

test('相似目录前缀不会被误判为项目子目录', () => {
  assert.equal(
    resolveProjectScriptPath('deploy.sh', '/tmp/project-other/deploy.sh', '/tmp/project'),
    'deploy.sh',
  )
})

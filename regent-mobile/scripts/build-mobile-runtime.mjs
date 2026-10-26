import fs from 'node:fs'
import path from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageJson = JSON.parse(
  fs.readFileSync(path.join(projectDirectory, 'package.json'), 'utf8'),
)
const shellInfo = JSON.parse(
  fs.readFileSync(path.join(projectDirectory, 'mobile-shell.json'), 'utf8'),
)
const releaseCode = process.env.REGENT_RELEASE_CODE?.trim()
if (!releaseCode || !/^R\d+B\d+$/.test(releaseCode)) {
  throw new Error('缺少合法的 REGENT_RELEASE_CODE')
}

const distribution = path.join(projectDirectory, 'dist')
const indexPath = path.join(distribution, 'index.html')
if (!fs.existsSync(indexPath)) {
  throw new Error(`移动端构建产物不存在：${indexPath}`)
}

const archive = path.join(
  distribution,
  `Regent-Mobile-${shellInfo.version}-${releaseCode}-runtime.zip`,
)
fs.rmSync(archive, { force: true })
const result = spawnSync('zip', ['-qry', archive, '.'], {
  cwd: distribution,
  stdio: 'inherit',
})
if (result.error) throw result.error
if (result.status !== 0) {
  throw new Error(`移动端 Runtime 压缩失败，退出码 ${result.status}`)
}

console.log(archive)

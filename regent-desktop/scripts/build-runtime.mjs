import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  createRuntimeArchive,
  stageRuntime,
} from './runtime-package.mjs'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const packageJson = JSON.parse(fs.readFileSync(path.join(projectDirectory, 'package.json'), 'utf8'))
const shellInfo = JSON.parse(fs.readFileSync(path.join(projectDirectory, 'shell-info.json'), 'utf8'))
const releaseCode = process.env.REGENT_RELEASE_CODE?.trim()
if (!releaseCode) {
  throw new Error('缺少 REGENT_RELEASE_CODE')
}
const artifactVersion = `${packageJson.version}-${releaseCode}`
const outputDirectory = path.join(projectDirectory, 'dist')
const workDirectory = path.join(outputDirectory, 'work')
const runtimeDirectory = path.join(workDirectory, 'runtime')
const runtimeArchive = path.join(
  outputDirectory,
  `Regent-${artifactVersion}-runtime.zip`,
)

fs.mkdirSync(outputDirectory, { recursive: true })
stageRuntime(projectDirectory, runtimeDirectory, {
  releaseCode,
  minShellRelease: shellInfo.shellRelease,
})
createRuntimeArchive(runtimeDirectory, runtimeArchive)

console.log(runtimeArchive)

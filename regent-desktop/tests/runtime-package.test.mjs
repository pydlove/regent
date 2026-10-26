import assert from 'node:assert/strict'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import test from 'node:test'
import { fileURLToPath } from 'node:url'

import { RUNTIME_FILES, stageRuntime } from '../scripts/runtime-package.mjs'

const projectDirectory = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

test('Runtime 包包含 main.js 的全部本地依赖', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'regent-runtime-package-'))
  try {
    stageRuntime(projectDirectory, directory, {
      releaseCode: 'R100B002',
      minShellRelease: 'R100B010',
    })

    assert.ok(RUNTIME_FILES.includes('update-policy.js'))
    assert.ok(RUNTIME_FILES.includes('update-preferences.js'))
    const source = fs.readFileSync(path.join(directory, 'main.js'), 'utf8')
    const dependencies = [...source.matchAll(/require\(['"]\.\/([^'"]+)['"]\)/g)]
      .map((match) => match[1])
    for (const dependency of dependencies) {
      const filename = path.extname(dependency) ? dependency : `${dependency}.js`
      assert.ok(
        fs.existsSync(path.join(directory, filename)),
        `Runtime 包缺少 main.js 依赖：${filename}`,
      )
    }
  } finally {
    fs.rmSync(directory, { recursive: true, force: true })
  }
})

const fs = require('node:fs')
const path = require('node:path')
const { validReleaseCode } = require('./update-policy')

function readSkippedRelease(file) {
  try {
    const value = JSON.parse(fs.readFileSync(file, 'utf8'))
    return validReleaseCode(value.skippedRelease) ? value.skippedRelease : null
  } catch {
    return null
  }
}

function writeSkippedRelease(file, releaseCode) {
  if (!validReleaseCode(releaseCode)) {
    throw new Error('跳过版本的发布码不合法')
  }
  const temporary = `${file}.tmp-${process.pid}`
  fs.mkdirSync(path.dirname(file), { recursive: true })
  fs.writeFileSync(
    temporary,
    `${JSON.stringify({ schemaVersion: 1, skippedRelease: releaseCode }, null, 2)}\n`,
    'utf8',
  )
  try {
    fs.renameSync(temporary, file)
  } catch {
    fs.copyFileSync(temporary, file)
    fs.rmSync(temporary, { force: true })
  }
  return releaseCode
}

module.exports = {
  readSkippedRelease,
  writeSkippedRelease,
}

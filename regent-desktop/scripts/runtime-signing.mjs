import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

export const DEFAULT_PRIVATE_KEY_FILE = path.join(
  os.homedir(),
  '.regent',
  'runtime-signing-private.pem',
)

export function privateKeyFile() {
  const configured = process.env.REGENT_RUNTIME_SIGNING_KEY?.trim()
  if (!configured) return DEFAULT_PRIVATE_KEY_FILE
  if (configured.includes('-----BEGIN PRIVATE KEY-----')) return null
  return path.resolve(configured)
}

export function loadPrivateKey() {
  const configured = process.env.REGENT_RUNTIME_SIGNING_KEY?.trim()
  if (configured?.includes('-----BEGIN PRIVATE KEY-----')) {
    return configured
  }
  const file = privateKeyFile()
  if (!file || !fs.existsSync(file)) {
    throw new Error(
      `找不到 Runtime 签名私钥：${file || '(REGENT_RUNTIME_SIGNING_KEY)'}。`
      + '先运行 node scripts/init-runtime-signing.mjs。',
    )
  }
  return fs.readFileSync(file, 'utf8')
}

export function signRuntime(content) {
  return crypto.sign(null, content, loadPrivateKey()).toString('base64url')
}

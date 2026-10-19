import crypto from 'node:crypto'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const mode = process.argv[2]
if (!['encrypt', 'decrypt'].includes(mode)) {
  console.error('用法：node scripts/encrypt-config.mjs encrypt|decrypt [值]')
  console.error('未传值时从 stdin 读取；主密钥来自 REGENT_CONFIG_KEY 或 ~/.regent/config.key')
  process.exit(2)
}

function readStdin() {
  return new Promise((resolve) => {
    let value = ''
    process.stdin.setEncoding('utf8')
    process.stdin.on('data', (chunk) => { value += chunk })
    process.stdin.on('end', () => resolve(value.trim()))
  })
}

function masterKey() {
  if (process.env.REGENT_CONFIG_KEY?.trim()) return process.env.REGENT_CONFIG_KEY.trim()
  const keyFile = path.join(os.homedir(), '.regent', 'config.key')
  if (fs.existsSync(keyFile)) return fs.readFileSync(keyFile, 'utf8').trim()
  console.error('未找到配置主密钥：REGENT_CONFIG_KEY 或 ~/.regent/config.key')
  process.exit(2)
}

const value = process.argv[3] ?? await readStdin()
const key = crypto.createHash('sha256').update(masterKey(), 'utf8').digest()

if (mode === 'encrypt') {
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv)
  const ciphertext = Buffer.concat([cipher.update(value, 'utf8'), cipher.final()])
  const tag = cipher.getAuthTag()
  const payload = Buffer.concat([iv, ciphertext, tag]).toString('base64url')
  console.log(`ENC(v1:${payload})`)
} else {
  if (!value.startsWith('ENC(v1:') || !value.endsWith(')')) {
    console.error('不是受支持的配置密文格式')
    process.exit(2)
  }
  const payload = Buffer.from(value.slice(7, -1), 'base64url')
  const iv = payload.subarray(0, 12)
  const tag = payload.subarray(payload.length - 16)
  const ciphertext = payload.subarray(12, payload.length - 16)
  const decipher = crypto.createDecipheriv('aes-256-gcm', key, iv)
  decipher.setAuthTag(tag)
  console.log(Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8'))
}

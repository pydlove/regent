import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import {
  DEFAULT_PRIVATE_KEY_FILE,
  privateKeyFile,
} from './runtime-signing.mjs'

const privateFile = privateKeyFile() || DEFAULT_PRIVATE_KEY_FILE
const publicFile = path.resolve(
  process.argv[process.argv.indexOf('--public-file') + 1]
  || path.join(process.cwd(), 'runtime-public-key.pem'),
)
const force = process.argv.includes('--force')

if (fs.existsSync(privateFile) && !force) {
  throw new Error(`签名私钥已经存在：${privateFile}。如需轮换请显式加 --force`)
}
if (fs.existsSync(publicFile) && !force) {
  throw new Error(`公钥文件已经存在：${publicFile}。如需轮换请显式加 --force`)
}

const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519')
fs.mkdirSync(path.dirname(privateFile), { recursive: true, mode: 0o700 })
fs.writeFileSync(
  privateFile,
  privateKey.export({ type: 'pkcs8', format: 'pem' }),
  { mode: 0o600 },
)
fs.mkdirSync(path.dirname(publicFile), { recursive: true })
fs.writeFileSync(
  publicFile,
  publicKey.export({ type: 'spki', format: 'pem' }),
  'utf8',
)
console.log(`private: ${privateFile}`)
console.log(`public: ${publicFile}`)

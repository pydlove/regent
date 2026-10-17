import assert from 'node:assert/strict'
import test from 'node:test'

import { nextEnvironmentCopyKey } from '../src/utils/environmentCopy.ts'

const source = { scope: 'GLOBAL', projectId: null, key: 'DB_HOST' }

test('uses the first available copy suffix', () => {
  assert.equal(nextEnvironmentCopyKey(source, [source]), 'DB_HOST_COPY')
  assert.equal(
    nextEnvironmentCopyKey(source, [source, { ...source, key: 'db_host_copy' }]),
    'DB_HOST_COPY_2',
  )
})

test('continues after an existing numbered copy', () => {
  const copied = { ...source, key: 'DB_HOST_COPY_2' }
  assert.equal(
    nextEnvironmentCopyKey(copied, [
      source,
      { ...source, key: 'DB_HOST_COPY' },
      copied,
    ]),
    'DB_HOST_COPY_3',
  )
})

test('only checks keys in the same owner scope', () => {
  assert.equal(
    nextEnvironmentCopyKey(source, [
      { scope: 'PROJECT', projectId: 1, key: 'DB_HOST_COPY' },
    ]),
    'DB_HOST_COPY',
  )
})

test('keeps the generated key within the domain limit', () => {
  const longSource = { ...source, key: `A${'B'.repeat(127)}` }
  const generated = nextEnvironmentCopyKey(longSource, [longSource])
  assert.equal(generated.length, 128)
  assert.match(generated, /^AB+_COPY$/)
})

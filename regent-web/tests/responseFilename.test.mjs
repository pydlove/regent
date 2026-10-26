import assert from 'node:assert/strict'
import test from 'node:test'

const { filenameFromContentDisposition } = await import('../src/api/http.ts')

test('解析普通文件名', () => {
  assert.equal(
    filenameFromContentDisposition('attachment; filename="Regent-members.xlsx"'),
    'Regent-members.xlsx',
  )
})

test('优先解析 RFC 5987 中文文件名', () => {
  assert.equal(
    filenameFromContentDisposition(
      "attachment; filename=\"members.xlsx\"; filename*=UTF-8''%E7%BB%84%E7%BB%87%E6%88%90%E5%91%98.xlsx",
    ),
    '组织成员.xlsx',
  )
})

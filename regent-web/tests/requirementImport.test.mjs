import assert from 'node:assert/strict'
import test from 'node:test'

const { findDuplicateImportRows } = await import('../src/utils/requirementImport.ts')

const row = (title, rawText) => ({ title, rawText })

test('同一项目内按名称识别已有重复需求，不受版本和描述影响', () => {
  const errors = findDuplicateImportRows(
    [row('  登录优化 ', '支持\n扫码登录'), row('登录优化', '支持 扫码登录')],
    [
      { title: '登录优化' },
    ],
  )

  assert.deepEqual(errors, ['需求池已存在同名需求', '需求池已存在同名需求'])
})

test('名称不同但描述相同不算重复，文件内部同名仍会跳过', () => {
  const errors = findDuplicateImportRows(
    [row('报表导出', '导出 Excel'), row('报表下载', '导出 Excel'), row('报表导出', '导出 CSV')],
    [{ title: '数据导出', rawText: '导出 Excel' }],
  )

  assert.deepEqual(errors, [null, null, '与文件第 1 行名称重复'])
})

test('空标题交给原有必填校验，不参与重复判断', () => {
  const errors = findDuplicateImportRows(
    [row('', '描述'), row('   ', '描述')],
    [],
  )

  assert.deepEqual(errors, [null, null])
})

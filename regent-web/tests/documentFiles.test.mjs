import assert from 'node:assert/strict'
import test from 'node:test'

import {
  documentExtension,
  filesFromClipboard,
  isDocumentFile,
} from '../src/utils/documentFiles.ts'

test('文档扩展名识别不区分大小写并拒绝图片', () => {
  assert.equal(documentExtension('需求说明.PDF'), '.pdf')
  assert.equal(isDocumentFile({ name: '需求说明.PDF' }), true)
  assert.equal(isDocumentFile({ name: '接口清单.xlsx' }), true)
  assert.equal(isDocumentFile({ name: 'design.png' }), false)
})

test('剪贴板文件可被识别为文档，优先读取 items', () => {
  const pdf = { name: '需求说明.pdf', type: 'application/pdf' }
  const markdown = { name: '执行说明.md', type: 'text/markdown' }
  const files = filesFromClipboard({
    items: [
      { kind: 'string', getAsFile: () => null },
      { kind: 'file', getAsFile: () => pdf },
      { kind: 'file', getAsFile: () => markdown },
    ],
    files: [],
  })

  assert.deepEqual(files.map((file) => file.name), ['需求说明.pdf', '执行说明.md'])
})

test('部分浏览器只提供 files 时仍可粘贴文档', () => {
  const docx = { name: '方案.docx', type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
  const files = filesFromClipboard({ items: [], files: [docx] })

  assert.deepEqual(files, [docx])
  assert.equal(isDocumentFile(docx), true)
})

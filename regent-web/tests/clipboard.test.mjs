import assert from 'node:assert/strict'
import test from 'node:test'

const { copyTextToClipboard } = await import('../src/utils/clipboard.ts')

test('优先通过异步剪贴板写入原始文本', async () => {
  let copied = ''
  const result = await copyTextToClipboard('任务 #42', {
    clipboard: {
      async writeText(value) {
        copied = value
      },
    },
  })

  assert.equal(result, true)
  assert.equal(copied, '任务 #42')
})

test('剪贴板被拒绝时使用临时文本框兜底', async () => {
  let appended = null
  let removed = false
  let selected = false
  const field = {
    value: '',
    className: '',
    setAttribute() {},
    select() {
      selected = true
    },
    remove() {
      removed = true
    },
  }
  const result = await copyTextToClipboard('203.0.113.10', {
    clipboard: {
      async writeText() {
        throw new Error('permission denied')
      },
    },
    document: {
      createElement() {
        return field
      },
      body: {
        appendChild(value) {
          appended = value
        },
      },
      execCommand(command) {
        return command === 'copy'
      },
    },
  })

  assert.equal(result, true)
  assert.equal(field.value, '203.0.113.10')
  assert.equal(appended, field)
  assert.equal(selected, true)
  assert.equal(removed, true)
})

test('空内容和两条复制路径都不可用时返回失败', async () => {
  assert.equal(await copyTextToClipboard(''), false)
  assert.equal(await copyTextToClipboard('plan-name', { clipboard: null, document: null }), false)
})

import assert from 'node:assert/strict'
import test from 'node:test'

const { parseInline, parseMarkdown } = await import('../src/utils/markdown.ts')

const textOf = (tokens) =>
  tokens
    .map((token) => {
      if (token.type === 'text' || token.type === 'code') return token.text
      if (token.type === 'break') return '\n'
      return textOf(token.children)
    })
    .join('')

test('解析项目资料常用块：标题、列表、代码、引用、表格和分隔线', () => {
  const blocks = parseMarkdown(`# 项目资料

## 技术栈

- Java 17
- Vue 3

1. 运行测试
2. 查看结果

\`\`\`bash
mvn test
\`\`\`

> 只读分析

| 模块 | 职责 |
| --- | --- |
| core | 领域逻辑 |

---`)

  assert.deepEqual(
    blocks.map((block) => block.type),
    ['heading', 'heading', 'list', 'list', 'code', 'quote', 'table', 'rule'],
  )
  assert.deepEqual(blocks[2].items, ['Java 17', 'Vue 3'])
  assert.equal(blocks[3].ordered, true)
  assert.equal(blocks[4].language, 'bash')
  assert.equal(blocks[4].content, 'mvn test')
  assert.deepEqual(blocks[6].headers, ['模块', '职责'])
  assert.deepEqual(blocks[6].rows, [['core', '领域逻辑']])
})

test('段落保留换行，普通文本与代码样式可解析', () => {
  const blocks = parseMarkdown('第一行\n第二行\n\n使用 `mvn verify` 和 **重点**。')

  assert.equal(blocks[0].content, '第一行\n第二行')
  assert.equal(textOf(parseInline(blocks[1].content)), '使用 mvn verify 和 重点。')
})

test('只允许安全链接协议，原始 HTML 只作为文本', () => {
  const safe = parseInline('[文档](https://example.com) [锚点](#项目资料)')
  assert.deepEqual(
    safe.filter((token) => token.type === 'link').map((token) => token.href),
    ['https://example.com', '#项目资料'],
  )

  const unsafe = parseInline('[危险](javascript:alert(1)) <script>alert(1)</script>')
  assert.equal(unsafe.some((token) => token.type === 'link'), false)
  assert.match(textOf(unsafe), /javascript:alert\(1\)/)
  assert.match(textOf(unsafe), /<script>alert\(1\)<\/script>/)
})

test('未闭合或不完整的 Markdown 细节不会阻断其余内容', () => {
  const blocks = parseMarkdown('## 标题\n\n`未闭合\n\n**仍未闭合')

  assert.equal(blocks[0].type, 'heading')
  assert.equal(blocks[1].content, '`未闭合')
  assert.equal(blocks[2].content, '**仍未闭合')
  assert.equal(textOf(parseInline(blocks[2].content)), '**仍未闭合')
})

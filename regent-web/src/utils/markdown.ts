export type InlineToken =
  | { type: 'text'; text: string }
  | { type: 'code'; text: string }
  | { type: 'strong' | 'emphasis' | 'strike'; children: InlineToken[] }
  | { type: 'link'; href: string; children: InlineToken[] }
  | { type: 'break' }

export type MarkdownBlock =
  | { type: 'heading'; level: number; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; language: string; content: string }
  | { type: 'quote'; content: string }
  | { type: 'rule' }
  | { type: 'table'; headers: string[]; rows: string[][] }

const LIST_ITEM = /^ {0,3}([-+*]|\d+[.)])\s+(.*)$/
const TABLE_SEPARATOR_CELL = /^:?-{3,}:?$/

const fenceInfo = (line: string) => line.match(/^ {0,3}(`{3,}|~{3,})\s*([^\s`]*)?.*$/)

const headingInfo = (line: string) => line.match(/^ {0,3}(#{1,6})\s+(.+?)\s*#*\s*$/)

const isRule = (line: string) =>
  /^ {0,3}(?:(?:\*\s*){3,}|(?:-\s*){3,}|(?:_\s*){3,})$/.test(line)

const splitTableRow = (line: string) =>
  line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim())

const isTableStart = (lines: string[], index: number) => {
  const header = lines[index]
  const separator = lines[index + 1]
  if (!header?.includes('|') || !separator?.includes('|')) return false
  const cells = splitTableRow(separator)
  return cells.length > 0 && cells.every((cell) => TABLE_SEPARATOR_CELL.test(cell))
}

const isBlockStart = (lines: string[], index: number) => {
  const line = lines[index]
  if (line === undefined || line.trim() === '') return true
  return (
    fenceInfo(line) !== null ||
    headingInfo(line) !== null ||
    isRule(line) ||
    /^ {0,3}>\s?/.test(line) ||
    LIST_ITEM.test(line) ||
    isTableStart(lines, index)
  )
}

/**
 * 将 Codex 生成的项目摘要解析成可安全交给 Vue 渲染的结构。
 *
 * 这里不生成 HTML。原始 HTML 会按普通文本保留，既不执行也不注入 DOM。
 */
export function parseMarkdown(markdown: string): MarkdownBlock[] {
  const lines = markdown.replace(/\r\n?/g, '\n').split('\n')
  while (lines[0]?.trim() === '') lines.shift()
  while (lines.at(-1)?.trim() === '') lines.pop()

  const blocks: MarkdownBlock[] = []
  let index = 0

  while (index < lines.length) {
    const line = lines[index]
    if (line.trim() === '') {
      index += 1
      continue
    }

    const fence = fenceInfo(line)
    if (fence) {
      const marker = fence[1]
      const language = fence[2] ?? ''
      const content: string[] = []
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith(marker)) {
        content.push(lines[index])
        index += 1
      }
      if (index < lines.length) index += 1
      blocks.push({ type: 'code', language, content: content.join('\n') })
      continue
    }

    const heading = headingInfo(line)
    if (heading) {
      blocks.push({
        type: 'heading',
        level: heading[1].length,
        content: heading[2].trim(),
      })
      index += 1
      continue
    }

    if (isRule(line)) {
      blocks.push({ type: 'rule' })
      index += 1
      continue
    }

    if (/^ {0,3}>\s?/.test(line)) {
      const quote: string[] = []
      while (index < lines.length) {
        const match = lines[index].match(/^ {0,3}>\s?(.*)$/)
        if (!match) break
        quote.push(match[1])
        index += 1
      }
      blocks.push({ type: 'quote', content: quote.join('\n') })
      continue
    }

    const listItem = line.match(LIST_ITEM)
    if (listItem) {
      const ordered = /^\d/.test(listItem[1])
      const items: string[] = []
      while (index < lines.length) {
        const item = lines[index].match(LIST_ITEM)
        if (!item || /^\d/.test(item[1]) !== ordered) break
        let content = item[2].trim()
        index += 1
        while (
          index < lines.length &&
          lines[index].trim() !== '' &&
          !LIST_ITEM.test(lines[index]) &&
          !isBlockStart(lines, index) &&
          /^\s{2,}/.test(lines[index])
        ) {
          content += `\n${lines[index].trim()}`
          index += 1
        }
        items.push(content)
      }
      blocks.push({ type: 'list', ordered, items })
      continue
    }

    if (isTableStart(lines, index)) {
      const headers = splitTableRow(lines[index])
      index += 2
      const rows: string[][] = []
      while (
        index < lines.length &&
        lines[index].trim() !== '' &&
        lines[index].includes('|') &&
        !isRule(lines[index])
      ) {
        const row = splitTableRow(lines[index])
        rows.push(headers.map((_, cellIndex) => row[cellIndex] ?? ''))
        index += 1
      }
      blocks.push({ type: 'table', headers, rows })
      continue
    }

    const paragraph: string[] = [line.trim()]
    index += 1
    while (index < lines.length && !isBlockStart(lines, index)) {
      paragraph.push(lines[index].trim())
      index += 1
    }
    blocks.push({ type: 'paragraph', content: paragraph.join('\n') })
  }

  return blocks
}

function safeHref(value: string): string | null {
  const href = value.trim().replace(/^<|>$/g, '')
  if (/^(?:https?:\/\/|mailto:)/i.test(href)) return href
  if (/^#[^\s]+$/.test(href)) return href
  if (/^\/(?!\/)/.test(href)) return href
  return null
}

function appendText(tokens: InlineToken[], text: string) {
  if (!text) return
  const previous = tokens.at(-1)
  if (previous?.type === 'text') {
    previous.text += text
  } else {
    tokens.push({ type: 'text', text })
  }
}

function findClosing(source: string, marker: string, start: number): number {
  let cursor = start
  while (cursor < source.length) {
    const found = source.indexOf(marker, cursor)
    if (found < 0) return -1
    if (source[found - 1] !== '\\') return found
    cursor = found + marker.length
  }
  return -1
}

function parseLink(source: string, start: number): { length: number; token: InlineToken } | null {
  let labelEnd = start + 1
  let bracketDepth = 0
  for (; labelEnd < source.length; labelEnd += 1) {
    if (source[labelEnd] === '\\') {
      labelEnd += 1
      continue
    }
    if (source[labelEnd] === '[') bracketDepth += 1
    if (source[labelEnd] !== ']') continue
    if (bracketDepth === 0) break
    bracketDepth -= 1
  }
  if (labelEnd >= source.length || source[labelEnd + 1] !== '(') return null

  const urlEnd = findClosing(source, ')', labelEnd + 2)
  if (urlEnd < 0) return null
  const href = safeHref(source.slice(labelEnd + 2, urlEnd))
  if (!href) return null

  return {
    length: urlEnd - start + 1,
    token: {
      type: 'link',
      href,
      children: parseInline(source.slice(start + 1, labelEnd)),
    },
  }
}

export function parseInline(source: string): InlineToken[] {
  const tokens: InlineToken[] = []
  let index = 0

  while (index < source.length) {
    const character = source[index]

    if (character === '\\' && index + 1 < source.length && /[\\`*_[\]()#+.!~>-]/.test(source[index + 1])) {
      appendText(tokens, source[index + 1])
      index += 2
      continue
    }

    if (character === '\n') {
      tokens.push({ type: 'break' })
      index += 1
      continue
    }

    if (character === '`') {
      const close = findClosing(source, '`', index + 1)
      if (close > index + 1) {
        tokens.push({ type: 'code', text: source.slice(index + 1, close) })
        index = close + 1
        continue
      }
    }

    if (source.startsWith('**', index) || source.startsWith('__', index)) {
      const marker = source.slice(index, index + 2)
      const close = findClosing(source, marker, index + 2)
      if (close > index + 2) {
        tokens.push({
          type: 'strong',
          children: parseInline(source.slice(index + 2, close)),
        })
        index = close + 2
        continue
      }
    }

    if (source.startsWith('~~', index)) {
      const close = findClosing(source, '~~', index + 2)
      if (close > index + 2) {
        tokens.push({
          type: 'strike',
          children: parseInline(source.slice(index + 2, close)),
        })
        index = close + 2
        continue
      }
    }

    if (character === '[') {
      const link = parseLink(source, index)
      if (link) {
        tokens.push(link.token)
        index += link.length
        continue
      }
    }

    if (character === '*' || character === '_') {
      const close = findClosing(source, character, index + 1)
      if (close > index + 1) {
        tokens.push({
          type: 'emphasis',
          children: parseInline(source.slice(index + 1, close)),
        })
        index = close + 1
        continue
      }
    }

    if (character === '<') {
      const close = source.indexOf('>', index + 1)
      if (close > index + 1) {
        const value = source.slice(index + 1, close)
        if (/^(?:https?:\/\/|mailto:)/i.test(value)) {
          tokens.push({
            type: 'link',
            href: value,
            children: [{ type: 'text', text: value }],
          })
          index = close + 1
          continue
        }
      }
    }

    appendText(tokens, character)
    index += 1
  }

  return tokens
}

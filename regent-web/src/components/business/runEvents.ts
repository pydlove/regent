import type { RunEvent } from '@/types/api'
import { formatTokens } from '@/utils/format'

/*
 * 事件流的「人话翻译」。
 *
 * 原始事件是给机器看的：类型是英文枚举，关键内容（说了什么、跑了哪条命令、输出是什么）
 * 埋在两层 JSON 里，同一件事还被拆成 started / completed 两条。这里把它翻成一条条
 * 能读的记录：合并同一 item 的开始与结束、把执行者的话提成正文、把命令与输出提成终端块。
 *
 * 只做「提取与合并」，不做解释性改写：认不准的事件落成 raw 原样展示。真实内容永远还能
 * 在「原始事件」页签逐字对照 —— 排错时以那份为准，这份是给人快速过过程的。
 */

type Json = Record<string, unknown>

type Base = { seq: number; at: string; technical?: boolean }

/** 文件改动：kind 是 codex 给的原值（update / add / delete…），中文映射在展示层做 */
export type RunFileChange = { kind: string; path: string }

export type RunTodo = { text: string; completed: boolean }

export type RunHumanItem = Base &
  (
    | { kind: 'note'; title: string; detail: string | null }
    | { kind: 'say'; text: string }
    | { kind: 'report'; status: string; summary: string | null }
    | { kind: 'think'; text: string }
    | { kind: 'run'; command: string; exitCode: number | null; output: string; running: boolean }
    | { kind: 'file'; changes: RunFileChange[]; running: boolean }
    | { kind: 'todo'; todos: RunTodo[] }
    | { kind: 'decision'; decisionId: number | null; level: string | null; status: string | null }
    | { kind: 'error'; name: string }
    | { kind: 'raw'; text: string }
  )

export function translateRunEvents(events: RunEvent[]): RunHumanItem[] {
  const items: RunHumanItem[] = []
  const positionByItemId = new Map<string, number>()

  /* 同一 item 的开始与结束只占一个位置：后到的事件把先到的那条整体替换掉，
     所以 todo 的勾选会更新、命令的输出会在跑完后补齐。 */
  const put = (itemId: string | null, item: RunHumanItem) => {
    const existing = itemId === null ? undefined : positionByItemId.get(itemId)
    if (existing === undefined) {
      items.push(item)
      if (itemId !== null) {
        positionByItemId.set(itemId, items.length - 1)
      }
    } else {
      items[existing] = item
    }
  }

  for (const event of events) {
    const base: Base = { seq: event.seq, at: event.occurredAt }
    const payload = parseJson(event.payload)
    const node = asObject(payload)
    /* 类型以 run_event.event_type 列为准：平台自己产生的事件 payload 里没有 type 字段，
       codex 事件虽然在 payload 里带 type，但两边本来就同源。 */
    const type = event.type
    if (node === null || type === 'raw') {
      /* 解析出来是字符串，说明这行不是 JSON、被适配器原样包了一层（见 CodexJsonlParser） */
      items.push({ ...base, kind: 'raw', text: typeof payload === 'string' ? payload : event.payload })
      continue
    }

    if (type === 'turn.started') {
      continue
    }
    if (type === 'thread.started') {
      items.push({
        ...base,
        technical: true,
        kind: 'note',
        title: '开始执行',
        detail: shortId(asString(node.thread_id)),
      })
      continue
    }
    if (type === 'turn.completed') {
      items.push({
        ...base,
        technical: true,
        kind: 'note',
        title: '本轮结束',
        detail: usageText(node.usage),
      })
      continue
    }
    if (type === 'item.started' || type === 'item.completed' || type === 'item.updated') {
      const item = asObject(node.item)
      const human = item ? toHuman(item, base) : null
      if (human) {
        put(asString(item?.id ?? null), human)
      } else {
        items.push({ ...base, kind: 'raw', text: pretty(node) })
      }
      continue
    }
    if (type === 'platform.decision') {
      items.push({
        ...base,
        kind: 'decision',
        decisionId: asNumber(node.decisionId),
        level: asString(node.effectiveLevel),
        status: asString(node.status),
      })
      continue
    }
    if (type === 'agent.claim') {
      items.push({ ...base, kind: 'note', title: '外部执行者认领任务', detail: asString(node.agent) })
      continue
    }
    if (type === 'agent.report') {
      /* 外部执行者只报结论不报摘要（见 AGENT_PROTOCOL.md 的 report 端点） */
      items.push({ ...base, kind: 'report', status: asString(node.status) ?? 'UNKNOWN', summary: null })
      continue
    }
    if (type === 'platform.error') {
      items.push({ ...base, kind: 'error', name: asString(node.error) ?? '未知错误' })
      continue
    }
    if (type === 'platform.lease-expired') {
      items.push({ ...base, kind: 'note', title: '外部执行者失联，租约已回收', detail: asString(node.agent) })
      continue
    }
    items.push({ ...base, kind: 'raw', text: pretty(node) })
  }

  return items
}

function toHuman(item: Json, base: Base): RunHumanItem | null {
  const type = asString(item.type)
  const text = asString(item.text)
  switch (type) {
    case 'agent_message': {
      if (text === null) {
        return null
      }
      /* 执行器的最终汇报就是出参 JSON 本身（平台读它判结论），原样摆着没人看得懂 —— 提成结论行 */
      const report = asObject(parseJson(text))
      const status = report ? asString(report.status) : null
      if (status === 'DONE' || status === 'FAILED' || status === 'BLOCKED') {
        return { ...base, kind: 'report', status, summary: asString(report?.summary ?? null) }
      }
      return { ...base, kind: 'say', text }
    }
    case 'reasoning':
      return text === null ? null : { ...base, kind: 'think', text }
    case 'command_execution':
      return {
        ...base,
        kind: 'run',
        command: asString(item.command) ?? '',
        exitCode: asNumber(item.exit_code),
        /* stdout 惯常带前导空行，裁掉首尾空白只为省地方，中间内容一个字节不动 */
        output: (asString(item.aggregated_output) ?? '').trim(),
        running: asString(item.status) === 'in_progress',
      }
    case 'file_change':
      return {
        ...base,
        kind: 'file',
        changes: changeList(item.changes),
        running: asString(item.status) === 'in_progress',
      }
    case 'todo_list':
      return { ...base, kind: 'todo', todos: todoList(item.items) }
    default:
      /* 早期输出的推理项没有 type 字段、只有一段 text，它就是内心独白 */
      return type === null && text !== null ? { ...base, kind: 'think', text } : null
  }
}

function usageText(usage: unknown): string | null {
  const parsed = asObject(usage)
  if (!parsed) {
    return null
  }
  const parts: string[] = []
  const input = asNumber(parsed.input_tokens) ?? 0
  const output = asNumber(parsed.output_tokens) ?? 0
  const cached = asNumber(parsed.cached_input_tokens) ?? 0
  if (input > 0) {
    parts.push(`输入 ${formatTokens(input)}`)
  }
  if (cached > 0) {
    parts.push(`缓存 ${formatTokens(cached)}`)
  }
  if (output > 0) {
    parts.push(`输出 ${formatTokens(output)}`)
  }
  return parts.length > 0 ? parts.join(' · ') : null
}

function changeList(value: unknown): RunFileChange[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((raw) => {
    const change = asObject(raw)
    const path = change ? asString(change.path) : null
    return path ? [{ kind: asString(change?.kind) ?? 'update', path }] : []
  })
}

function todoList(value: unknown): RunTodo[] {
  if (!Array.isArray(value)) {
    return []
  }
  return value.flatMap((raw) => {
    const todo = asObject(raw)
    const text = todo ? asString(todo.text) : null
    return text ? [{ text, completed: todo?.completed === true }] : []
  })
}

const shortId = (id: string | null) => (id === null ? null : id.slice(0, 8))

const pretty = (node: Json) => JSON.stringify(node, null, 2)

function parseJson(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function asObject(value: unknown): Json | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Json) : null
}

function asString(value: unknown): string | null {
  return typeof value === 'string' && value.length > 0 ? value : null
}

function asNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

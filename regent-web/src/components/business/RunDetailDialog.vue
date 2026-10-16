<script setup lang="ts">
import { ScrollText } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import RunStatusBadge from '@/components/business/RunStatusBadge.vue'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import type { TaskRunDetail } from '@/types/api'
import { runIsFailure } from '@/types/task'
import { formatDuration, formatRelative, formatTokens } from '@/utils/format'

/*
 * 某一次执行的详情：事实在前，原文在后。
 *
 * 「事实」是给人扫一眼的（跑成什么样、花了多少、动了哪些文件），「原文」是给人查问题的
 * （事件流与末条消息）。所以默认停在事件流上，而且每条折叠到三行 ——
 * 一条 `item.completed` 里塞几千字是常态，全展开会让人滑到怀疑人生。
 *
 * 这一层只做展示与折叠，不做任何解析加工：payload 原样呈现，是不是 JSON、里面写了什么，
 * 由看日志的人自己判断。日志被「美化」过之后，排错时就会骗人。
 */
const props = withDefaults(
  defineProps<{
    detail: TaskRunDetail | null
    loading?: boolean
    error?: string | null
  }>(),
  { loading: false, error: null },
)

const open = defineModel<boolean>('open', { default: false })

const tab = ref<'events' | 'raw'>('events')
const expanded = ref<number[]>([])

// 每次重新打开都回到事件流、收起全部长条目：上一次翻到哪跟这一次要看的东西没关系
watch(open, (value) => {
  if (value) {
    tab.value = 'events'
    expanded.value = []
  }
})

const run = computed(() => props.detail?.run ?? null)

const tabItems = computed(() => [
  { value: 'events', label: `事件流（${props.detail?.events.length ?? 0}）` },
  { value: 'raw', label: '出参原文' },
])

const toggle = (seq: number) => {
  expanded.value = expanded.value.includes(seq)
    ? expanded.value.filter((value) => value !== seq)
    : [...expanded.value, seq]
}

const isExpanded = (seq: number) => expanded.value.includes(seq)

/** 事件类型前缀分出「执行器说的」与「平台记的」——排查时第一眼要能分开 */
const isPlatformEvent = (type: string) => type.startsWith('platform.')

/** 能解析就缩进，解析不了就原样 —— 非 JSON 行本来就允许出现（见 CodexJsonlParser） */
const pretty = (payload: string) => {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}

const tokenBreakdown = computed(() => {
  const tokens = run.value
  if (!tokens) return '—'
  return `输入 ${formatTokens(tokens.tokenInput)} · 缓存 ${formatTokens(tokens.tokenCachedInput)} · 输出 ${formatTokens(tokens.tokenOutput)}`
})
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :title="run ? `第 ${run.attemptNo} 次尝试` : '运行记录'"
    :description="run ? `${run.branchName} · ${formatRelative(run.startedAt)} 开始` : '正在读取这次执行的记录'"
    :icon="ScrollText"
  >
    <p v-if="loading" class="text-caption text-label-secondary">正在读取…</p>
    <p v-else-if="error" class="text-caption text-danger">{{ error }}</p>

    <template v-else-if="run">
      <div class="flex items-center gap-2">
        <RunStatusBadge :status="run.status" />
        <Badge v-if="!run.schemaValid" tone="warning">出参不合约定</Badge>
        <span v-if="run.exitCode !== null" class="tnum text-caption text-label-tertiary">
          退出码 {{ run.exitCode }}
        </span>
        <span class="tnum ml-auto text-caption text-label-tertiary">
          {{ formatDuration(run.startedAt, run.finishedAt) }}
        </span>
      </div>

      <dl class="mt-3 grid grid-cols-2 gap-x-6 gap-y-1.5">
        <div class="flex items-center gap-2">
          <dt class="text-caption text-label-secondary">消耗</dt>
          <dd class="tnum ml-auto text-caption text-label">{{ formatTokens(run.tokens) }}</dd>
        </div>
        <div class="flex items-center gap-2">
          <dt class="text-caption text-label-secondary">构成</dt>
          <dd class="tnum ml-auto truncate text-caption text-label-secondary">{{ tokenBreakdown }}</dd>
        </div>
      </dl>

      <p
        v-if="run.summary"
        :class="[
          'mt-3 rounded-panel border border-separator-soft bg-content p-2.5 text-caption',
          runIsFailure(run.status) ? 'text-danger' : 'text-label',
        ]"
      >
        {{ run.summary }}
      </p>
      <p v-if="!run.schemaValid" class="mt-1.5 text-caption text-label-tertiary">
        平台没能按约定读出结论，这次只能记成失败 —— 原文见「出参原文」。
      </p>

      <div v-if="run.changedFiles.length > 0" class="mt-3">
        <p class="text-caption font-emphasis text-label-secondary">改动文件</p>
        <ul class="mt-1 max-h-24 overflow-y-auto font-mono text-caption text-label-secondary">
          <li v-for="file in run.changedFiles" :key="file" class="truncate">{{ file }}</li>
        </ul>
      </div>

      <SegmentedControl v-model="tab" :items="tabItems" size="sm" class="mt-4" />

      <div
        v-if="tab === 'events'"
        class="mt-2 max-h-[46vh] overflow-y-auto rounded-panel border border-separator-soft bg-content p-2"
      >
        <p v-if="detail && detail.events.length === 0" class="p-2 text-caption text-label-tertiary">
          这次执行没有留下事件流。
        </p>
        <div
          v-for="event in detail?.events ?? []"
          :key="event.seq"
          class="border-b border-separator-soft px-1 py-1.5 last:border-b-0"
        >
          <div class="flex items-center gap-2">
            <span class="tnum w-8 shrink-0 text-caption text-label-tertiary">{{ event.seq }}</span>
            <Badge v-if="isPlatformEvent(event.type)" tone="accent">{{ event.type }}</Badge>
            <span v-else class="font-mono text-caption text-label">{{ event.type }}</span>
            <span class="tnum ml-auto shrink-0 text-caption text-label-tertiary">
              {{ formatRelative(event.occurredAt) }}
            </span>
          </div>
          <pre
            :class="[
              'mt-1 ml-10 whitespace-pre-wrap break-all font-mono text-caption text-label-secondary',
              isExpanded(event.seq) ? '' : 'line-clamp-3',
            ]"
          >{{ pretty(event.payload) }}</pre>
          <button
            v-if="event.payload.length > 200"
            class="ml-10 mt-0.5 text-caption text-accent"
            type="button"
            @click="toggle(event.seq)"
          >
            {{ isExpanded(event.seq) ? '收起' : '展开全部' }}
          </button>
        </div>
      </div>

      <div
        v-else
        class="mt-2 max-h-[46vh] overflow-y-auto rounded-panel border border-separator-soft bg-content p-2.5"
      >
        <pre class="whitespace-pre-wrap break-all font-mono text-caption text-label-secondary">{{
          detail?.rawResult ?? '（这次执行没有落盘末条消息）'
        }}</pre>
      </div>
    </template>

    <template #footer>
      <Button size="sm" @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import {
  Check,
  Code2,
  Eye,
  EyeOff,
  Maximize2,
  MessageSquareText,
  Minimize2,
  RefreshCw,
  ScrollText,
  X,
} from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import MarkdownContent from '@/components/business/MarkdownContent.vue'
import RunStatusBadge from '@/components/business/RunStatusBadge.vue'
import RunHumanEntry from '@/components/business/RunHumanEntry.vue'
import { translateRunEvents, type RunHumanItem } from '@/components/business/runEvents'
import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import type { DecisionSummary, TaskRunDetail } from '@/types/api'
import { runIsFailure } from '@/types/task'
import { formatDuration, formatRelative, formatTokens } from '@/utils/format'

/*
 * 某一次执行的详情：事实在前，原文在后。
 *
 * 默认只展示用户能读的执行对话和结论，最新内容在最上面。思考、命令、文件改动等
 * 技术细节默认收起；原始事件与出参原文放进独立的「技术日志」视图，排错时再按需查看。
 *
 * 原始事件页签刻意不做任何加工：payload 原样呈现，是不是 JSON、里面写了什么，由看日志的
 * 人自己判断 —— 日志被「美化」过之后，排错时就会骗人。人话视图只做提取与合并，认不准的
 * 事件原样落到「其他事件」，两侧永远能对上。
 */
const props = withDefaults(
  defineProps<{
    detail: TaskRunDetail | null
    decision?: DecisionSummary | null
    decisionLoading?: boolean
    loading?: boolean
    refreshing?: boolean
    error?: string | null
  }>(),
  { decision: null, decisionLoading: false, loading: false, refreshing: false, error: null },
)

const open = defineModel<boolean>('open', { default: false })
const emit = defineEmits<{
  approve: []
  refresh: []
  reject: []
}>()

const technicalView = ref<'events' | 'raw' | null>(null)
const showTechnicalDetails = ref(false)
const expanded = ref<number[]>([])
const fullscreen = ref(false)

// 每次重新打开都回到执行对话、收起全部技术条目：上一次翻到哪跟这一次要看的东西没关系
watch(open, (value) => {
  fullscreen.value = false
  if (value) {
    technicalView.value = null
    showTechnicalDetails.value = false
    expanded.value = []
  }
})

const run = computed(() => props.detail?.run ?? null)

/** 外部执行者跑在平台看不见的进程里，界面必须能把它与本机 codex 分开 —— 它失联的样子是一条停在原地的租约 */
const isExternal = computed(() => run.value?.executor === 'agent')

const executorLabel = computed(() => {
  if (!run.value) {
    return ''
  }
  return isExternal.value ? `外部执行者 · ${run.value.agentName ?? '未署名'}` : '本机 codex（平台拉起）'
})

const leaseLabel = computed(() => {
  const expiresAt = run.value?.leaseExpiresAt
  if (!expiresAt) {
    return '已收场，不再占用工作区'
  }
  return `${formatRelative(expiresAt)} 到期`
})

const decisionReady = computed(
  () =>
    run.value !== null
    && props.decision?.status === 'PENDING'
    && props.decision.taskId === run.value.taskId,
)
const showDecisionActions = computed(
  () =>
    run.value?.status === 'BLOCKED'
    && (props.decisionLoading || decisionReady.value),
)

/** 人话视图里条目的「谁在说」 */
const speaker = computed(() => (isExternal.value ? (run.value?.agentName ?? '执行者') : 'codex'))

// 人话视图是给人快速扫「刚刚发生了什么」的，最新在上；原始事件页仍保持正序。
const humanItems = computed(() => {
  const summary = run.value?.summary?.trim()
  return translateRunEvents(props.detail?.events ?? [])
    .filter((item) => !summary || item.kind !== 'report' || item.summary?.trim() !== summary)
    .slice()
    .reverse()
})

const isTechnicalItem = (item: RunHumanItem) =>
  item.technical === true
  || item.kind === 'think'
  || item.kind === 'run'
  || item.kind === 'file'
  || item.kind === 'todo'
  || item.kind === 'raw'

const technicalCount = computed(() => humanItems.value.filter(isTechnicalItem).length)
const visibleHumanItems = computed(() =>
  showTechnicalDetails.value
    ? humanItems.value
    : humanItems.value.filter((item) => !isTechnicalItem(item)),
)

const technicalTabItems = computed(() => [
  // 手机上两列各只有约 130px；全角括号会让事件数单独折行，空格分隔更稳。
  { value: 'events', label: `原始事件 ${props.detail?.events.length ?? 0}` },
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

const contentHeightClass = computed(() =>
  fullscreen.value ? 'max-h-[calc(100dvh-260px)]' : 'max-h-[36vh]',
)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    size="wide"
    :fullscreen="fullscreen"
    :max-height="fullscreen ? 'default' : 'compact'"
    :title="run ? `第 ${run.attemptNo} 次尝试` : '运行记录'"
    :description="run ? `${run.branchName ?? '项目目录'} · ${formatRelative(run.startedAt)} 开始` : '正在读取这次执行的记录'"
    :icon="ScrollText"
  >
    <p v-if="loading && !run" class="text-caption text-label-secondary">正在读取…</p>
    <p v-else-if="error && !run" class="text-caption text-danger">{{ error }}</p>

    <template v-else-if="run">
      <p v-if="error" class="mb-2 text-caption text-danger">{{ error }}</p>
      <div class="flex items-center gap-2">
        <RunStatusBadge :status="run.status" />
        <Badge v-if="isExternal" tone="neutral">外部执行者</Badge>
        <Badge v-if="!run.schemaValid" tone="warning">出参不合约定</Badge>
        <span v-if="run.exitCode !== null" class="tnum text-caption text-label-tertiary">
          退出码 {{ run.exitCode }}
        </span>
        <span class="tnum ml-auto text-caption text-label-tertiary">
          {{ formatDuration(run.startedAt, run.finishedAt) }}
        </span>
      </div>

      <dl class="mt-3 grid grid-cols-2 gap-x-6 gap-y-1.5">
        <template v-if="technicalView !== null">
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">消耗</dt>
            <dd class="tnum ml-auto text-caption text-label">{{ formatTokens(run.tokens) }}</dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">构成</dt>
            <dd class="tnum ml-auto truncate text-caption text-label-secondary">{{ tokenBreakdown }}</dd>
          </div>
        </template>
        <div class="col-span-2 flex items-center gap-2">
          <dt class="text-caption text-label-secondary">执行者</dt>
          <dd class="ml-auto truncate text-caption text-label">{{ executorLabel }}</dd>
        </div>
        <div v-if="run.deviceId" class="col-span-2 flex items-center gap-2">
          <dt class="text-caption text-label-secondary">设备标识</dt>
          <dd class="ml-auto max-w-[70%] truncate font-mono text-caption text-label-secondary">
            {{ run.deviceId }}
          </dd>
        </div>
        <div v-if="isExternal" class="col-span-2 flex items-center gap-2">
          <dt class="text-caption text-label-secondary">租约</dt>
          <dd class="tnum ml-auto text-caption text-label-secondary">{{ leaseLabel }}</dd>
        </div>
      </dl>

      <div
        v-if="run.summary"
        :class="[
          'mt-3 rounded-panel border border-separator-soft bg-content p-3',
          runIsFailure(run.status) ? 'text-danger' : 'text-label',
        ]"
      >
        <p class="text-caption font-emphasis">执行结论</p>
        <MarkdownContent class="mt-1.5 text-label" :markdown="run.summary" />
      </div>
      <p v-if="!run.schemaValid" class="mt-1.5 text-caption text-label-tertiary">
        平台没能按约定读出结论，这次只能记成失败 —— 原文见「出参原文」。
      </p>

      <div v-if="run.changedFiles.length > 0" class="mt-3">
        <p class="text-caption font-emphasis text-label-secondary">改动文件</p>
        <ul class="mt-1 max-h-24 overflow-y-auto font-mono text-caption text-label-secondary">
          <li v-for="file in run.changedFiles" :key="file" class="truncate">{{ file }}</li>
        </ul>
      </div>

      <div class="mt-4 flex flex-wrap items-center gap-2 border-b border-separator-soft pb-2">
        <template v-if="technicalView === null">
          <div class="flex min-w-0 items-center gap-1.5">
            <Icon :as="MessageSquareText" size="inline" class="text-accent" />
            <span class="text-caption font-emphasis text-label">执行对话</span>
            <span v-if="technicalCount > 0" class="text-caption text-label-tertiary">
              · {{ technicalCount }} 条执行细节已收起
            </span>
          </div>
          <button
            v-if="technicalCount > 0"
            type="button"
            class="ml-auto inline-flex items-center gap-1 text-caption text-accent"
            @click="showTechnicalDetails = !showTechnicalDetails"
          >
            <Icon :as="showTechnicalDetails ? EyeOff : Eye" size="inline" />
            {{ showTechnicalDetails ? '隐藏执行细节' : '显示执行细节' }}
          </button>
          <button
            type="button"
            class="inline-flex items-center gap-1 text-caption text-label-tertiary hover:text-label"
            @click="technicalView = 'events'"
          >
            <Icon :as="Code2" size="inline" />
            技术日志
          </button>
        </template>
        <template v-else>
          <Button size="sm" @click="technicalView = null">返回执行对话</Button>
          <SegmentedControl
            v-model="technicalView"
            :items="technicalTabItems"
            size="sm"
            class="ml-auto"
          />
        </template>
      </div>

      <div
        v-if="technicalView === null"
        class="mt-2 overflow-y-auto rounded-panel border border-separator-soft bg-content px-3.5 py-1"
        :class="contentHeightClass"
      >
        <p v-if="visibleHumanItems.length === 0" class="p-2 text-caption text-label-tertiary">
          {{ technicalCount > 0 ? '这次执行没有文字说明，技术细节已收起。' : '这次执行没有留下事件流。' }}
        </p>
        <div
          v-for="item in visibleHumanItems"
          :key="item.seq"
          class="py-2"
        >
          <RunHumanEntry :item="item" :speaker="speaker" />
        </div>
      </div>

      <div
        v-else-if="technicalView === 'events'"
        class="mt-2 overflow-y-auto rounded-panel border border-separator-soft bg-content p-2"
        :class="contentHeightClass"
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
        class="mt-2 overflow-y-auto rounded-panel border border-separator-soft bg-content p-2.5"
        :class="contentHeightClass"
      >
        <pre class="whitespace-pre-wrap break-all font-mono text-caption text-label-secondary">{{
          detail?.rawResult ?? '（这次执行没有落盘末条消息）'
        }}</pre>
      </div>
    </template>

    <template #footer>
      <div class="flex w-full flex-wrap justify-end gap-3">
        <Button size="sm" :disabled="refreshing || !run" @click="emit('refresh')">
          <Icon :as="RefreshCw" size="inline" :class="refreshing ? 'animate-spin' : ''" />
          {{ refreshing ? '刷新中…' : '刷新' }}
        </Button>
        <Button size="sm" @click="fullscreen = !fullscreen">
          <Icon :as="fullscreen ? Minimize2 : Maximize2" size="inline" />
          {{ fullscreen ? '退出全屏' : '全屏查看' }}
        </Button>
        <template v-if="showDecisionActions">
          <Button
            size="sm"
            :disabled="!decisionReady || decisionLoading"
            @click="emit('reject')"
          >
            <Icon :as="X" size="inline" />驳回
          </Button>
          <Button
            variant="primary"
            size="sm"
            :disabled="!decisionReady || decisionLoading"
            @click="emit('approve')"
          >
            <Icon :as="Check" size="inline" />
            {{ decisionLoading ? '正在读取决策…' : '批准并继续' }}
          </Button>
        </template>
        <Button size="sm" @click="open = false">关闭</Button>
      </div>
    </template>
  </Dialog>
</template>

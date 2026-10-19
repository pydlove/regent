<script setup lang="ts">
import { Coins, RefreshCw } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { getDailyTokenUsage, getHourlyTokenUsage } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Skeleton from '@/components/ui/skeleton/Skeleton.vue'
import type {
  DailyTokenUsagePoint,
  HourlyTokenUsagePoint,
  TokenUsageRange,
} from '@/types/api'
import { formatTokenYi, formatTokens } from '@/utils/format'

const props = defineProps<{
  projectId: number | null
  scopeLabel: string
}>()
const open = defineModel<boolean>('open', { default: false })

type UsagePoint = {
  key: string
  axisLabel: string
  fullLabel: string
  input: number
  cachedInput: number
  output: number
  total: number
  runCount: number
}

const rangeItems: { value: TokenUsageRange; label: string }[] = [
  { value: 'TODAY', label: '当天' },
  { value: 'LAST_24_HOURS', label: '近 24 小时' },
  { value: 'LAST_7_DAYS', label: '近 7 天' },
  { value: 'LAST_14_DAYS', label: '近 14 天' },
  { value: 'LAST_30_DAYS', label: '近 30 天' },
  { value: 'ALL', label: '全部' },
]
const metricItems = [
  { value: 'tokens', label: 'Tokens' },
  { value: 'runs', label: '次数' },
]

const range = ref<TokenUsageRange>('TODAY')
const metric = ref('tokens')
const points = ref<UsagePoint[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
let loadSequence = 0

const hourFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})
const dayFormatter = new Intl.DateTimeFormat('zh-CN', {
  month: '2-digit',
  day: '2-digit',
})
const fullDayFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'short',
})
const fullHourFormatter = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const hourly = computed(() => range.value === 'TODAY' || range.value === 'LAST_24_HOURS')
const rangeLabel = computed(
  () => rangeItems.find((item) => item.value === range.value)?.label ?? '',
)
const bucketLabel = computed(() => (hourly.value ? '小时' : '天'))
const scopeTitle = computed(() => `${props.scopeLabel} · 使用趋势 · ${rangeLabel.value}`)

function messageOf(value: unknown): string {
  if (value instanceof ApiError) return value.message
  return '读取 Token 明细失败，请确认后端服务正常'
}

function parseLocal(value: string): Date {
  return new Date(value.length === 16 ? `${value}:00` : value)
}

function dailyPoint(point: DailyTokenUsagePoint): UsagePoint {
  const date = parseLocal(`${point.date}T00:00:00`)
  return {
    key: point.date,
    axisLabel: dayFormatter.format(date),
    fullLabel: fullDayFormatter.format(date),
    input: point.input,
    cachedInput: point.cachedInput,
    output: point.output,
    total: point.total,
    runCount: point.runCount,
  }
}

function hourlyPoint(point: HourlyTokenUsagePoint): UsagePoint {
  const date = parseLocal(point.bucketStart)
  return {
    key: point.bucketStart,
    axisLabel: hourFormatter.format(date),
    fullLabel: fullHourFormatter.format(date),
    input: point.input,
    cachedInput: point.cachedInput,
    output: point.output,
    total: point.total,
    runCount: point.runCount,
  }
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  error.value = null
  try {
    const next = hourly.value
      ? (await getHourlyTokenUsage(range.value, props.projectId)).map(hourlyPoint)
      : (await getDailyTokenUsage(range.value, props.projectId)).map(dailyPoint)
    if (sequence === loadSequence) points.value = next
  } catch (value) {
    if (sequence !== loadSequence) return
    error.value = messageOf(value)
    points.value = []
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

watch(open, (opened) => {
  if (!opened) {
    loadSequence++
    return
  }
  if (range.value !== 'TODAY') {
    range.value = 'TODAY'
    return
  }
  void load()
})

watch([range, () => props.projectId], () => {
  if (open.value) void load()
})

const summary = computed(() =>
  points.value.reduce(
    (total, point) => ({
      input: total.input + point.input,
      cachedInput: total.cachedInput + point.cachedInput,
      output: total.output + point.output,
      runs: total.runs + point.runCount,
    }),
    { input: 0, cachedInput: 0, output: 0, runs: 0 },
  ),
)
const total = computed(() => summary.value.input + summary.value.cachedInput + summary.value.output)
const totalYi = computed(() => formatTokenYi(total.value))
const activeValue = (point: UsagePoint) => (metric.value === 'runs' ? point.runCount : point.total)
const maxValue = computed(() => Math.max(1, ...points.value.map(activeValue)))
const hasUsage = computed(() => points.value.some((point) => activeValue(point) > 0))
const plotMinWidth = computed(() => `${Math.max(440, points.value.length * 28)}px`)
const labelStep = computed(() => Math.max(1, Math.ceil(points.value.length / 8)))

function barHeight(point: UsagePoint): string {
  const value = activeValue(point)
  return value === 0 ? '0%' : `${Math.max(2.5, (value / maxValue.value) * 100)}%`
}

function shouldShowLabel(index: number): boolean {
  return index % labelStep.value === 0 || index === points.value.length - 1
}

function formatValue(value: number): string {
  return value === 0 ? '0' : formatTokens(value)
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    size="wide"
    max-height="compact"
    title="Token 消耗"
    :description="`${scopeLabel} · 按${bucketLabel}查看已结算执行记录`"
    :icon="Coins"
  >
    <div class="flex flex-col gap-4">
      <div class="scroll-area min-w-0 overflow-x-auto">
        <SegmentedControl v-model="range" :items="rangeItems" size="sm" class="min-w-[500px]" />
      </div>

      <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
        <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
          <p class="text-caption text-label-tertiary">总消耗</p>
          <p class="tnum mt-1 text-title font-emphasis text-label">{{ formatValue(total) }}</p>
          <p v-if="totalYi" class="tnum mt-0.5 text-caption text-label-tertiary">
            ≈ {{ totalYi }} 亿 tokens
          </p>
        </div>
        <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
          <p class="text-caption text-label-tertiary">输入</p>
          <p class="tnum mt-1 text-title font-emphasis text-accent">{{ formatValue(summary.input) }}</p>
        </div>
        <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
          <p class="text-caption text-label-tertiary">缓存输入</p>
          <p class="tnum mt-1 text-title font-emphasis text-warning">{{ formatValue(summary.cachedInput) }}</p>
        </div>
        <div class="rounded-panel border border-separator-soft bg-content px-3 py-2.5">
          <p class="text-caption text-label-tertiary">输出</p>
          <p class="tnum mt-1 text-title font-emphasis text-success">{{ formatValue(summary.output) }}</p>
        </div>
      </div>

      <div class="rounded-panel border border-separator-soft bg-content">
        <div class="flex flex-wrap items-center gap-3 border-b border-separator-soft px-4 py-3">
          <div class="min-w-0">
            <p class="text-caption font-emphasis text-label">{{ scopeTitle }}</p>
            <p class="mt-0.5 text-caption text-label-tertiary">
              {{ summary.runs }} 次执行 · {{ points.length }} 个{{ bucketLabel }}
            </p>
          </div>
          <div class="ml-auto">
            <SegmentedControl v-model="metric" :items="metricItems" size="sm" />
          </div>
        </div>

        <div v-if="loading" class="flex h-[260px] items-center justify-center px-4">
          <Skeleton class="h-[210px] w-full" />
        </div>
        <div v-else-if="error" class="flex items-center gap-3 px-4 py-10">
          <p class="min-w-0 flex-1 text-caption text-danger">{{ error }}</p>
          <Button size="sm" @click="load">
            <Icon :as="RefreshCw" size="inline" />重试
          </Button>
        </div>
        <div
          v-else-if="!hasUsage"
          class="px-4 py-14 text-center text-caption text-label-tertiary"
        >
          当前日期范围内还没有已结算的 Token 消耗
        </div>
        <div v-else class="px-4 pb-4 pt-5">
          <div class="flex gap-3">
            <div class="flex h-52 w-12 shrink-0 flex-col justify-between pb-7 text-right text-caption text-label-tertiary">
              <span class="tnum">{{ formatValue(maxValue) }}</span>
              <span class="tnum">{{ formatValue(Math.round(maxValue / 2)) }}</span>
              <span class="tnum">0</span>
            </div>

            <div class="scroll-area min-w-0 flex-1 overflow-x-auto">
              <div class="relative" :style="{ minWidth: plotMinWidth }">
                <div class="pointer-events-none absolute inset-x-0 top-0 h-44">
                  <span class="absolute inset-x-0 top-0 border-t border-separator-soft" />
                  <span class="absolute inset-x-0 top-1/2 border-t border-separator-soft" />
                  <span class="absolute inset-x-0 bottom-0 border-t border-separator" />
                </div>

                <div class="relative flex h-44 items-end gap-1.5">
                  <button
                    v-for="point in points"
                    :key="point.key"
                    type="button"
                    class="group relative flex h-full min-w-0 flex-1 cursor-default items-end justify-center"
                    :aria-label="`${point.fullLabel} ${metric === 'tokens' ? 'Token' : '执行'} ${activeValue(point)}`"
                  >
                    <span
                      class="relative block w-full max-w-9 rounded-t-[3px] bg-accent transition-[height,filter] duration-300 ease-apple group-hover:brightness-110"
                      :style="{ height: barHeight(point) }"
                    />
                    <span
                      class="pointer-events-none absolute bottom-[calc(100%+8px)] left-1/2 z-20 hidden w-max max-w-52 -translate-x-1/2 rounded-panel border border-separator-soft bg-content px-2.5 py-2 text-left shadow-popover group-hover:block"
                    >
                      <span class="block text-caption font-emphasis text-label">{{ point.fullLabel }}</span>
                      <span class="mt-1 flex items-center gap-2 text-caption text-label-secondary">
                        <span class="size-1.5 rounded-full bg-accent" />
                        {{ metric === 'tokens' ? 'Tokens' : '执行次数' }}
                        <span class="tnum ml-auto font-emphasis text-label">{{ activeValue(point) }}</span>
                      </span>
                    </span>
                  </button>
                </div>

                <div class="mt-2 flex gap-1.5">
                  <span
                    v-for="(point, index) in points"
                    :key="`${point.key}-label`"
                    class="min-w-0 flex-1 whitespace-nowrap text-center text-caption text-label-tertiary"
                  >
                    <span v-if="shouldShowLabel(index)">{{ point.axisLabel }}</span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <Button @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>

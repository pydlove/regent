<script setup lang="ts">
import { computed, useId } from 'vue'

import type { TrendDatum } from './chart.types'

const props = withDefaults(defineProps<{ points: TrendDatum[]; height?: number }>(), {
  height: 148,
})

const gradientId = `trend-area-${useId().replaceAll(':', '')}`

const peak = computed(() =>
  Math.max(1, ...props.points.map((point) => Math.max(point.done, point.failed))),
)

const columns = computed(() => {
  const last = Math.max(1, props.points.length - 1)
  return props.points.map((point, index) => ({
    ...point,
    x: props.points.length === 1 ? 50 : (index / last) * 100,
    doneY: point.done === 0 ? 100 : 100 - (point.done / peak.value) * 88,
    failedY: point.failed === 0 ? 100 : 100 - (point.failed / peak.value) * 88,
  }))
})

type CurvePoint = { x: number; y: number }
type CurveGeometry = { start: CurvePoint; segments: string[] }

const coordinate = (value: number) => Math.round(value * 100) / 100

/*
 * 单调三次 Hermite 曲线：既能把折角磨圆，又不会像普通样条那样在尖峰两侧冲出数据范围。
 * 趋势图一旦过冲到负值或高于真实峰值，读者会误判业务量，所以这里优先保证单调性。
 */
function buildCurve(points: CurvePoint[]): CurveGeometry | null {
  if (points.length === 0) return null
  if (points.length === 1) return { start: points[0], segments: [] }

  const slopes = points.slice(1).map((point, index) => {
    const previous = points[index]
    return (point.y - previous.y) / (point.x - previous.x)
  })
  const tangents = points.map((_, index) => {
    if (index === 0) return slopes[0]
    if (index === points.length - 1) return slopes.at(-1)!
    const previous = slopes[index - 1]
    const next = slopes[index]
    if (previous === 0 || next === 0 || Math.sign(previous) !== Math.sign(next)) return 0
    return (previous + next) / 2
  })

  for (let index = 0; index < slopes.length; index += 1) {
    if (slopes[index] === 0) {
      tangents[index] = 0
      tangents[index + 1] = 0
      continue
    }
    const previousRatio = tangents[index] / slopes[index]
    const nextRatio = tangents[index + 1] / slopes[index]
    const magnitude = previousRatio ** 2 + nextRatio ** 2
    if (magnitude > 9) {
      const scale = 3 / Math.sqrt(magnitude)
      tangents[index] = scale * previousRatio * slopes[index]
      tangents[index + 1] = scale * nextRatio * slopes[index]
    }
  }

  const segments = slopes.map((_, index) => {
    const from = points[index]
    const to = points[index + 1]
    const width = to.x - from.x
    const control1X = from.x + width / 3
    const control1Y = from.y + (tangents[index] * width) / 3
    const control2X = to.x - width / 3
    const control2Y = to.y - (tangents[index + 1] * width) / 3
    return ` C ${coordinate(control1X)} ${coordinate(control1Y)} ${coordinate(control2X)} ${coordinate(control2Y)} ${coordinate(to.x)} ${coordinate(to.y)}`
  })

  return { start: points[0], segments }
}

const doneCurve = computed(() =>
  buildCurve(columns.value.map((column) => ({ x: column.x, y: column.doneY }))),
)
const failedCurve = computed(() =>
  buildCurve(columns.value.map((column) => ({ x: column.x, y: column.failedY }))),
)
const donePath = computed(() =>
  doneCurve.value
    ? `M ${coordinate(doneCurve.value.start.x)} ${coordinate(doneCurve.value.start.y)}${doneCurve.value.segments.join('')}`
    : '',
)
const failedPath = computed(() =>
  failedCurve.value
    ? `M ${coordinate(failedCurve.value.start.x)} ${coordinate(failedCurve.value.start.y)}${failedCurve.value.segments.join('')}`
    : '',
)
const areaPath = computed(() => {
  const curve = doneCurve.value
  if (!curve) return ''
  const last = columns.value.at(-1)!
  return `M ${coordinate(curve.start.x)} 100 L ${coordinate(curve.start.x)} ${coordinate(curve.start.y)}${curve.segments.join('')} L ${coordinate(last.x)} 100 Z`
})

const failureTotal = computed(() => props.points.reduce((sum, point) => sum + point.failed, 0))
const summary = computed(() =>
  props.points
    .map((point) => `${point.label}完成 ${point.done} 项、失败 ${point.failed} 项`)
    .join('；'),
)

const labelTransform = (index: number) => {
  if (index === 0) return 'translateX(0)'
  if (index === columns.value.length - 1) return 'translateX(-100%)'
  return 'translateX(-50%)'
}
</script>

<template>
  <div class="min-w-0" :style="{ minHeight: `${height + 22}px` }">
    <div
      class="relative"
      :style="{ height: `${height}px` }"
      role="img"
      :aria-label="`近 ${points.length} 天任务趋势：${summary}`"
    >
      <div
        v-for="line in 4"
        :key="line"
        class="pointer-events-none absolute inset-x-0 border-t border-separator-soft"
        :style="{ top: `${(line - 1) * (100 / 3)}%` }"
      />

      <svg
        class="absolute inset-0 size-full overflow-visible"
        viewBox="0 0 100 100"
        preserveAspectRatio="none"
        aria-hidden="true"
      >
        <defs>
          <linearGradient :id="gradientId" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="var(--sys-green)" stop-opacity="0.24" />
            <stop offset="100%" stop-color="var(--sys-green)" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path :d="areaPath" :fill="`url(#${gradientId})`" />
        <path
          :d="donePath"
          fill="none"
          stroke="var(--sys-green)"
          stroke-width="1.6"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
        <path
          v-if="failureTotal > 0"
          :d="failedPath"
          fill="none"
          stroke="var(--sys-red)"
          stroke-opacity="0.68"
          stroke-width="1.3"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
        />
      </svg>

      <div
        class="pointer-events-none absolute top-0 bottom-0 border-l border-dashed border-success/20"
        :style="{ left: '100%' }"
      />

      <template v-for="(column, index) in columns" :key="column.key">
        <div
          v-if="column.done > 0"
          class="absolute -translate-x-1/2 -translate-y-1/2"
          :style="{ left: `${column.x}%`, top: `${column.doneY}%` }"
          :title="`${column.label}：完成 ${column.done} 项`"
        >
          <span
            class="block rounded-full border-2 border-content bg-success shadow-control"
            :class="column.highlight ? 'size-2.5' : 'size-2'"
          />
        </div>
        <span
          v-if="column.done > 0"
          class="tnum pointer-events-none absolute -translate-x-1/2 -translate-y-[calc(100%+8px)] rounded-badge bg-content/90 px-1 text-subhead font-emphasis text-label-secondary"
          :style="{ left: `${column.x}%`, top: `${column.doneY}%` }"
        >
          {{ column.done }}
        </span>

        <div
          v-if="column.failed > 0"
          class="absolute -translate-x-1/2 -translate-y-1/2"
          :style="{ left: `${column.x}%`, top: `${column.failedY}%` }"
          :title="`${column.label}：失败 ${column.failed} 项`"
        >
          <span class="block size-2 rounded-full border-2 border-content bg-danger shadow-control" />
        </div>

        <span
          class="absolute top-full mt-1.5 whitespace-nowrap text-subhead"
          :class="column.highlight ? 'font-emphasis text-label' : 'text-label-tertiary'"
          :style="{ left: `${column.x}%`, transform: labelTransform(index) }"
        >
          {{ column.label }}
        </span>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { DonutSegment } from './chart.types'

const props = withDefaults(
  defineProps<{
    segments: DonutSegment[]
    size?: number
    thickness?: number
    /** 段与段之间的可见空隙（px，沿圆周方向） */
    gap?: number
    /** 底环，单段进度环用它表示「还没走到的部分」 */
    trackClass?: string
    centerValue?: string
    centerLabel?: string
  }>(),
  {
    size: 132,
    thickness: 11,
    gap: 3,
    trackClass: 'text-neutral/15',
    centerValue: undefined,
    centerLabel: undefined,
  },
)

const radius = computed(() => (props.size - props.thickness) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)
const total = computed(() => props.segments.reduce((sum, segment) => sum + segment.value, 0))

/**
 * 圆头线帽会向两端各多画半个线宽，所以每段要额外让出 `thickness` 才留得出看得见的空隙。
 * 只有一段且占满整圈时不留空隙 —— 那种情况下缺口没有含义，只是破了一根完整的环。
 */
const arcs = computed(() => {
  const visible = props.segments.filter((segment) => segment.value > 0)
  if (total.value === 0) return []
  const shrink = visible.length > 1 ? props.gap + props.thickness : 0
  let consumed = 0
  return visible.map((segment) => {
    const length = (segment.value / total.value) * circumference.value
    const drawn = Math.max(length - shrink, 1)
    const arc = {
      ...segment,
      dash: `${drawn} ${circumference.value - drawn}`,
      offset: -consumed,
    }
    consumed += length
    return arc
  })
})
</script>

<template>
  <div
    class="relative inline-grid shrink-0 place-items-center"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <!-- 从 12 点方向顺时针起画，和 macOS 的进度环一致 -->
    <svg
      class="-rotate-90"
      :width="size"
      :height="size"
      :viewBox="`0 0 ${size} ${size}`"
      role="img"
    >
      <circle
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="currentColor"
        :stroke-width="thickness"
        :class="trackClass"
      />
      <circle
        v-for="arc in arcs"
        :key="arc.key"
        :cx="size / 2"
        :cy="size / 2"
        :r="radius"
        fill="none"
        stroke="currentColor"
        :stroke-width="thickness"
        stroke-linecap="round"
        :stroke-dasharray="arc.dash"
        :stroke-dashoffset="arc.offset"
        :class="[arc.toneClass, 'transition-[stroke-dasharray,stroke-dashoffset] duration-500 ease-apple']"
      >
        <title>{{ arc.label }} {{ arc.value }}</title>
      </circle>
    </svg>
    <div class="absolute inset-0 grid place-content-center text-center">
      <span class="tnum text-metric font-bold text-label">{{ centerValue }}</span>
      <span v-if="centerLabel" class="mt-0.5 text-caption text-label-tertiary">
        {{ centerLabel }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { TrendBar } from './chart.types'

const props = withDefaults(defineProps<{ bars: TrendBar[]; height?: number }>(), { height: 116 })

const peak = computed(() => Math.max(1, ...props.bars.map((bar) => bar.done + bar.failed)))

const columns = computed(() =>
  props.bars.map((bar) => {
    const total = bar.done + bar.failed
    return {
      ...bar,
      total,
      // 失败占比用 flex-basis 表达：百分比高度在 flex 列里容易算不准，交给布局引擎更稳
      failedShare: total === 0 ? 0 : (bar.failed / total) * 100,
      heightPercent: total === 0 ? 0 : (total / peak.value) * 100,
    }
  }),
)
</script>

<template>
  <!--
    只画柱子，不画整条空轨道：这一行多半只有今天有数据，六根满高的灰槽看着像还没加载完。
    零值的那天留一根短桩，说明这天算过、只是没有收场任务。
  -->
  <div>
    <div class="flex items-end gap-2 border-b border-separator-soft" :style="{ height: `${height}px` }">
      <div
        v-for="column in columns"
        :key="column.key"
        class="group relative h-full min-w-0 flex-1"
      >
        <span
          class="pointer-events-none absolute left-1/2 top-0 z-10 -translate-x-1/2 whitespace-nowrap rounded-badge bg-material px-1.5 py-0.5 text-caption text-label opacity-0 shadow-popover backdrop-blur-[30px] backdrop-saturate-150 transition-opacity duration-100 ease-apple group-hover:opacity-100"
        >
          完成 {{ column.done }} · 失败 {{ column.failed }}
        </span>

        <div
          v-if="column.total > 0"
          class="absolute inset-x-0 bottom-0 flex flex-col justify-end overflow-hidden rounded-t-[4px] transition-[height] duration-500 ease-apple"
          :style="{ height: `${column.heightPercent}%` }"
        >
          <div
            v-if="column.failed > 0"
            class="w-full bg-danger/85"
            :style="{ flex: `0 0 ${column.failedShare}%` }"
          />
          <div
            class="w-full flex-1 transition-colors duration-100 ease-apple"
            :class="column.highlight ? 'bg-success' : 'bg-success/55 group-hover:bg-success/75'"
          />
        </div>
        <div v-else class="absolute inset-x-0 bottom-0 h-[3px] rounded-full bg-neutral/20" />
      </div>
    </div>

    <div class="mt-1.5 flex gap-2">
      <span
        v-for="column in columns"
        :key="column.key"
        class="min-w-0 flex-1 truncate text-center text-caption"
        :class="column.highlight ? 'font-emphasis text-label' : 'text-label-tertiary'"
      >
        {{ column.label }}
      </span>
    </div>
  </div>
</template>

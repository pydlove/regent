<script setup lang="ts">
import { ChevronLeft, ChevronRight } from '@lucide/vue'
import { computed } from 'vue'

import Icon from '@/components/ui/icon/Icon.vue'
import Select from '@/components/ui/select/Select.vue'

/*
 * 表格底部的分页条。
 *
 * 为什么不是「上一页 / 下一页」两个按钮：macOS 里连着的翻页动作是一个**步进控件**
 * （NSSegmentedControl），两段合在一个圆角容器里，中间一条发丝线。两个独立的圆角按钮
 * 一是显得像两个功能，二是和工具栏里的图标按钮撞脸。这里按前者做。
 *
 * 左边只放读数（1–20 / 共 49 条），右边放「每页多少」和翻页。
 * 读数用 50% 的标签色（secondary）而不是 25%（tertiary）：这一条里唯一的文字信息就是它，
 * 压到 25% 在白底上已经发虚，而这数字是用户真要读的（「一共多少条」）。
 */
const props = withDefaults(
  defineProps<{
    page: number
    pageSize: number
    total: number
    pageSizes?: number[]
  }>(),
  { pageSizes: () => [20, 50, 100] },
)

const emit = defineEmits<{ 'update:page': [number]; 'update:pageSize': [number] }>()

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))
const rangeStart = computed(() => (props.total === 0 ? 0 : (props.page - 1) * props.pageSize + 1))
const rangeEnd = computed(() => Math.min(props.page * props.pageSize, props.total))
const canPrev = computed(() => props.page > 1)
const canNext = computed(() => props.page < pageCount.value)

const sizeItems = computed(() =>
  props.pageSizes.map((size) => ({ value: String(size), label: `${size} 条` })),
)

// Select 的 model 是 string，这里换算成 number，免得把字符串页码漏进 store
const sizeModel = computed({
  get: () => String(props.pageSize),
  set: (value: string | undefined) => {
    if (value) emit('update:pageSize', Number(value))
  },
})

const go = (delta: number) => {
  const next = props.page + delta
  if (next >= 1 && next <= pageCount.value) emit('update:page', next)
}

// h-full 而不是 h-control：外层带 1px 边框，两段若各自写 24px，整体就变 26px，
// 和旁边 24px 的下拉差 1px，凑近看就是「没对齐」
const stepClass =
  'flex h-full w-control items-center justify-center text-label-secondary transition-colors duration-[120ms] ease-apple enabled:hover:bg-hover enabled:hover:text-label disabled:opacity-40'
</script>

<template>
  <div class="flex h-footer shrink-0 items-center gap-3 border-t border-separator bg-window px-5">
    <p class="tnum text-caption text-label-secondary">
      <template v-if="total > 0">{{ rangeStart }}–{{ rangeEnd }} / 共 {{ total }} 条</template>
      <template v-else>没有可显示的记录</template>
    </p>

    <div class="ml-auto flex items-center gap-3">
      <Select v-model="sizeModel" :items="sizeItems" class="w-[88px]" :disabled="total === 0" />

      <span class="tnum text-caption text-label-secondary">{{ page }} / {{ pageCount }}</span>

      <div class="flex h-control items-center overflow-hidden rounded-control border border-separator">
        <button
          type="button"
          :class="stepClass"
          :disabled="!canPrev"
          aria-label="上一页"
          title="上一页"
          @click="go(-1)"
        >
          <Icon :as="ChevronLeft" size="inline" />
        </button>
        <span class="h-full w-px shrink-0 bg-separator" />
        <button
          type="button"
          :class="stepClass"
          :disabled="!canNext"
          aria-label="下一页"
          title="下一页"
          @click="go(1)"
        >
          <Icon :as="ChevronRight" size="inline" />
        </button>
      </div>
    </div>
  </div>
</template>

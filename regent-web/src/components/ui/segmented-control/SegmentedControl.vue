<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    items: { value: string; label: string; disabled?: boolean }[]
    size?: 'sm' | 'regular' | 'lg'
    disabled?: boolean
  }>(),
  { size: 'regular', disabled: false },
)

const model = defineModel<string>({ required: true })

const activeIndex = computed(() => {
  const index = props.items.findIndex((item) => item.value === model.value)
  return index < 0 ? 0 : index
})

const trackHeight = computed(
  () => ({ sm: 'h-control-sm', regular: 'h-control', lg: 'h-control-lg' })[props.size],
)
const thumbWidth = computed(() => `${100 / props.items.length}%`)
// 小号控件优先出现在窄窗口的弹框里，标签长度不可控。缩字号与横向内边距换一行高度，
// 比让「原始事件（258）」折成两行更像原生分段控件。
const itemClasses = computed(() =>
  props.size === 'sm' ? 'px-1 text-caption sm:px-1.5 sm:text-subhead' : 'px-2.5 text-body',
)
</script>

<template>
  <!-- macOS 最标志性的控件：灰色轨道 + 白色滑块。
       滑块用 inset-px 的内层容器承载，百分比才能与等分后的段宽精确对齐。 -->
  <div
    role="radiogroup"
    :class="[
      'relative inline-grid auto-cols-fr grid-flow-col rounded-[7px] bg-neutral/15 p-px',
      trackHeight,
    ]"
  >
    <div class="pointer-events-none absolute inset-px">
      <div
        class="h-full rounded-[6px] bg-content shadow-control transition-transform duration-[160ms] ease-apple"
        :style="{ width: thumbWidth, transform: `translateX(${activeIndex * 100}%)` }"
      />
    </div>

    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      role="radio"
      :aria-checked="item.value === model"
      :disabled="disabled || item.disabled"
      :class="[
        'relative z-10 whitespace-nowrap rounded-[6px] transition-colors duration-[120ms] ease-apple disabled:opacity-40',
        itemClasses,
        item.value === model
          ? 'font-emphasis text-label'
          : 'text-label-secondary hover:text-label',
      ]"
      @click="model = item.value"
    >
      {{ item.label }}
    </button>
  </div>
</template>

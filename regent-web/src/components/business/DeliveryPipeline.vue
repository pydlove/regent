<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  steps: Array<{ value: string; label: string }>
  current: string
  cancelled?: boolean
}>()

const currentIndex = computed(() => {
  const index = props.steps.findIndex((step) => step.value === props.current)
  return index < 0 ? 0 : index
})

const stateOf = (index: number) => {
  if (props.cancelled) return index <= currentIndex.value ? 'failed' : 'upcoming'
  if (index < currentIndex.value) return 'done'
  if (index === currentIndex.value) return 'current'
  return 'upcoming'
}

const dotClass = (index: number) => {
  const state = stateOf(index)
  if (state === 'failed') return 'bg-danger'
  if (state === 'done') return 'bg-success'
  if (state === 'current') return 'bg-accent ring-4 ring-accent/15'
  return 'bg-neutral/20'
}

const lineClass = (index: number) => {
  const state = stateOf(index)
  if (state === 'failed') return 'bg-danger/30'
  if (state === 'done') return 'bg-success/35'
  return 'bg-separator'
}

const labelClass = (index: number) => {
  const state = stateOf(index)
  if (state === 'current') return 'text-label font-emphasis'
  if (state === 'failed') return 'text-danger'
  return state === 'done' ? 'text-label-secondary' : 'text-label-tertiary'
}
</script>

<template>
  <ol class="space-y-0">
    <li
      v-for="(step, index) in steps"
      :key="step.value"
      class="flex gap-2.5"
    >
      <span class="flex w-2 shrink-0 flex-col items-center">
        <span
          class="mt-1.5 size-2 rounded-full transition-colors"
          :class="dotClass(index)"
        />
        <span
          v-if="index < steps.length - 1"
          class="my-1 min-h-4 w-px flex-1"
          :class="lineClass(index)"
        />
      </span>
      <div class="min-w-0 pb-3 last:pb-0">
        <p class="text-caption leading-5" :class="labelClass(index)">{{ step.label }}</p>
        <p v-if="stateOf(index) === 'current'" class="text-[10px] text-label-tertiary">
          当前阶段
        </p>
      </div>
    </li>
  </ol>
</template>

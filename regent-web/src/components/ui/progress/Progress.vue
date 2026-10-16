<script setup lang="ts">
import { ProgressIndicator, ProgressRoot } from 'reka-ui'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ value?: number; tone?: 'accent' | 'success' | 'warning' | 'danger' }>(),
  { value: 0, tone: 'accent' },
)

const barClass = computed(
  () =>
    ({
      accent: 'bg-accent',
      success: 'bg-success',
      warning: 'bg-warning',
      danger: 'bg-danger',
    })[props.tone],
)
</script>

<template>
  <ProgressRoot :model-value="value" class="h-1 w-full overflow-hidden rounded-full bg-neutral/20">
    <ProgressIndicator :class="['h-full rounded-full transition-[width] duration-200 ease-apple', barClass]" />
  </ProgressRoot>
</template>

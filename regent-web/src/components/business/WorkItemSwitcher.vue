<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'

const props = defineProps<{ value: 'TASKS' | 'BUGS' }>()

const router = useRouter()

const options = [
  { value: 'TASKS', label: '任务' },
  { value: 'BUGS', label: 'Bug 单' },
]

const view = computed({
  get: () => props.value,
  set: (next: string) => {
    if (next === props.value) return
    void router.push(next === 'BUGS' ? '/tasks?view=bugs' : '/tasks')
  },
})
</script>

<template>
  <SegmentedControl v-model="view" :items="options" size="lg" />
</template>

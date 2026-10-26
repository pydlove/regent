<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'

type SectionTab = {
  value: string
  label: string
  badge?: number
}

const props = defineProps<{
  modelValue: string
  items: SectionTab[]
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const nav = ref<HTMLElement | null>(null)

async function revealActiveTab() {
  await nextTick()
  const active = nav.value?.querySelector<HTMLElement>('[aria-current="page"]')
  if (!active || !nav.value) return
  const left = active.offsetLeft - (nav.value.clientWidth - active.clientWidth) / 2
  nav.value.scrollTo({ left: Math.max(0, left), behavior: 'smooth' })
}

watch(() => props.modelValue, revealActiveTab)
onMounted(revealActiveTab)
</script>

<template>
  <nav
    ref="nav"
    class="flex min-h-control shrink-0 items-end gap-1 overflow-x-auto border-b border-separator-soft px-3"
  >
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="relative flex h-control shrink-0 items-center gap-1.5 px-2 text-caption text-label-secondary transition-colors hover:text-label focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
      :class="modelValue === item.value ? 'font-emphasis text-label' : ''"
      :aria-current="modelValue === item.value ? 'page' : undefined"
      @click="emit('update:modelValue', item.value)"
    >
      {{ item.label }}
      <span
        v-if="item.badge"
        class="tnum rounded-badge bg-neutral/15 px-1 text-[10px] leading-4 text-label-secondary"
      >
        {{ item.badge > 99 ? '99+' : item.badge }}
      </span>
      <span
        v-if="modelValue === item.value"
        class="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-accent"
        aria-hidden="true"
      />
    </button>
  </nav>
</template>

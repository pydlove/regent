<script setup lang="ts">
import { AvatarFallback, AvatarImage, AvatarRoot } from 'reka-ui'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{ src?: string; name: string; size?: 'sm' | 'regular' | 'lg' }>(),
  { src: '', size: 'regular' },
)

const sizeClass = computed(
  () => ({ sm: 'size-4 text-[8px]', regular: 'size-5 text-caption', lg: 'size-7 text-body' })[props.size],
)

const initials = computed(() => props.name.trim().slice(0, 1).toUpperCase())
</script>

<template>
  <AvatarRoot
    :class="['inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-neutral/25 text-label-secondary', sizeClass]"
  >
    <AvatarImage v-if="src" :src="src" :alt="name" class="size-full object-cover" />
    <AvatarFallback>{{ initials }}</AvatarFallback>
  </AvatarRoot>
</template>

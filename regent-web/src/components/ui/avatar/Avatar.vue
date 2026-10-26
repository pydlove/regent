<script setup lang="ts">
import { AvatarFallback, AvatarImage, AvatarRoot } from 'reka-ui'
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    src?: string
    name?: string
    size?: 'sm' | 'regular' | 'lg' | 'member' | 'profile'
  }>(),
  { src: '', name: '', size: 'regular' },
)

const sizeClass = computed(
  () => ({
    sm: 'size-4 text-[8px]',
    regular: 'size-5 text-caption',
    lg: 'size-7 text-body',
    member: 'size-9 text-body',
    profile: 'size-14 text-body',
  })[props.size],
)

const initials = computed(() => (props.name ?? '').trim().slice(0, 1).toUpperCase() || 'U')
</script>

<template>
  <AvatarRoot
    :class="['inline-flex shrink-0 select-none items-center justify-center overflow-hidden rounded-full bg-accent/10 text-accent', sizeClass]"
  >
    <AvatarImage v-if="src" :src="src" :alt="name" class="size-full object-cover" />
    <AvatarFallback>{{ initials }}</AvatarFallback>
  </AvatarRoot>
</template>

<script setup lang="ts">
import { Inbox } from '@lucide/vue'

import Icon from '@/components/ui/icon/Icon.vue'

withDefaults(defineProps<{ title: string; description?: string }>(), { description: '' })
</script>

<template>
  <div class="flex flex-col items-center justify-center gap-1 px-6 py-10 text-center">
    <slot name="icon">
      <Icon :as="Inbox" size="xlarge" class="text-label-tertiary" />
    </slot>
    <p class="mt-1 text-body font-emphasis text-label">{{ title }}</p>
    <p v-if="description" class="text-caption text-label-secondary">{{ description }}</p>
    <div class="mt-2">
      <slot />
    </div>
  </div>
</template>

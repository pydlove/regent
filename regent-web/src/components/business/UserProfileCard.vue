<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'

import Avatar from '@/components/ui/avatar/Avatar.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import type { AuthUser } from '@/types/api'

defineProps<{ user: AuthUser }>()
defineEmits<{ edit: [] }>()
</script>

<template>
  <button
    type="button"
    class="group mt-1 flex w-full items-center gap-2 rounded-[6px] px-2 py-2 text-left transition-colors duration-[120ms] ease-apple hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
    aria-label="编辑个人资料"
    @click="$emit('edit')"
  >
    <Avatar :src="user.avatarUrl ?? ''" :name="user.displayName || user.username" size="lg" />
    <span class="min-w-0 flex-1">
      <span class="block truncate text-body font-emphasis text-label">
        {{ user.displayName || user.username }}
      </span>
      <span class="mt-0.5 block truncate text-caption text-label-tertiary">
        {{ user.email ?? `@${user.username}` }}
      </span>
    </span>
    <Icon
      :as="ChevronRight"
      class="text-label-tertiary opacity-0 transition-opacity duration-100 group-hover:opacity-100 group-focus-visible:opacity-100"
    />
  </button>
</template>

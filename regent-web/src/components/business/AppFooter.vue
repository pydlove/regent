<script setup lang="ts">
import { Smartphone } from '@lucide/vue'

import AppVersionDialog from '@/components/business/AppVersionDialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'

const currentYear = new Date().getFullYear()
</script>

<template>
  <footer
    class="relative flex h-footer shrink-0 items-center border-t border-separator bg-window px-3 text-caption text-label-tertiary lg:px-5"
  >
    <p class="absolute left-1/2 -translate-x-1/2 whitespace-nowrap">
      Copyright © {{ currentYear }} Regent AI
    </p>
    <div class="ml-auto flex items-center gap-2">
      <RouterLink
        to="/download/app"
        class="inline-flex min-h-7 items-center gap-1 rounded-[6px] px-1.5 transition-colors hover:bg-hover hover:text-label"
        aria-label="下载手机 App"
      >
        <Icon :as="Smartphone" size="inline" />
        <span class="hidden sm:inline">手机 App</span>
      </RouterLink>
      <AppVersionDialog />
    </div>
  </footer>
</template>

<script setup lang="ts">
import AppVersionDialog from '@/components/business/AppVersionDialog.vue'

const currentYear = new Date().getFullYear()
</script>

<template>
  <footer
    class="relative flex h-footer shrink-0 items-center border-t border-separator bg-window px-3 text-caption text-label-tertiary lg:px-5"
  >
    <p class="absolute left-1/2 -translate-x-1/2 whitespace-nowrap">
      Copyright © {{ currentYear }} Regent AI
    </p>
    <div class="ml-auto flex items-center">
      <AppVersionDialog />
    </div>
  </footer>
</template>

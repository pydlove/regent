<script setup lang="ts">
import AppVersionDialog from '@/components/business/AppVersionDialog.vue'

const currentYear = new Date().getFullYear()
</script>

<template>
  <footer
    class="flex h-footer shrink-0 items-center gap-2 border-t border-separator bg-window px-3 text-caption text-label-tertiary lg:px-5"
  >
    <p>Copyright © {{ currentYear }} Regent AI</p>
    <span aria-hidden="true" class="text-label-tertiary/60">·</span>
    <AppVersionDialog />
    <p class="ml-auto hidden sm:block">Regent AI 任务执行平台</p>
  </footer>
</template>

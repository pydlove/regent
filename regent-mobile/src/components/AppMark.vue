<script setup lang="ts">
defineProps<{
  compact?: boolean
}>()
</script>

<template>
  <div class="app-mark" :class="{ compact }" aria-hidden="true">
    <span class="app-mark-orbit"></span>
    <span class="app-mark-core">R</span>
  </div>
</template>

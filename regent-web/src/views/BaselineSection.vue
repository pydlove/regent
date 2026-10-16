<script setup lang="ts">
defineProps<{ title: string; hint?: string }>()
</script>

<template>
  <section class="mb-7">
    <div class="mb-2.5 flex items-baseline gap-2">
      <h2 class="text-body font-emphasis text-label">
        {{ title }}
      </h2>
      <p v-if="hint" class="text-caption text-label-tertiary">{{ hint }}</p>
    </div>
    <div class="rounded-panel bg-content p-5 shadow-panel">
      <slot />
    </div>
  </section>
</template>

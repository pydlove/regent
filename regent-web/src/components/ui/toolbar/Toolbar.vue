<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <header
    :class="[
      'relative flex shrink-0 items-center gap-2 border-b border-separator bg-material px-3 backdrop-blur-[30px] backdrop-saturate-150',
      compact ? 'h-toolbar-compact' : 'h-toolbar',
    ]"
  >
    <slot name="leading" />
    <!-- 标题居中：macOS 工具栏的标题是居中的，左右两边留给操作 -->
    <div class="pointer-events-none absolute inset-x-0 flex justify-center">
      <div class="pointer-events-auto min-w-0">
        <slot />
      </div>
    </div>
    <div class="ml-auto flex items-center gap-2">
      <slot name="trailing" />
    </div>
  </header>
</template>

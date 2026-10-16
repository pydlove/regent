<script setup lang="ts">
import type { TaskAttachment } from '@/types/api'

/*
 * 已落库图片附件的缩略图墙。
 *
 * 只画瓦片，不画标题、不管弹框 —— 用它的地方（任务详情侧栏、看图弹框的缩略图条）标题各不相同。
 * 瓦片用等宽等高的方形：截图有宽有窄，按原比例排会让这一块看起来像没对齐的拼贴。
 */
withDefaults(defineProps<{ attachments: TaskAttachment[]; size?: 'sm' | 'md' }>(), {
  size: 'md',
})

const emit = defineEmits<{ select: [attachment: TaskAttachment] }>()
</script>

<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="attachment in attachments"
      :key="attachment.id"
      type="button"
      class="overflow-hidden rounded-panel border border-separator-soft bg-content transition-colors duration-100 ease-apple hover:border-accent/60 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
      :class="size === 'sm' ? 'size-10' : 'size-16'"
      :title="attachment.filename"
      @click="emit('select', attachment)"
    >
      <img
        :src="attachment.contentUrl"
        :alt="attachment.filename"
        loading="lazy"
        class="size-full object-cover"
      />
    </button>
  </div>
</template>

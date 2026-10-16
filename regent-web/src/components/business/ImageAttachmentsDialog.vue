<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import ImageAttachmentGrid from '@/components/business/ImageAttachmentGrid.vue'
import Button from '@/components/ui/button/Button.vue'
import { buttonVariants } from '@/components/ui/button/button.variants'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import type { ImageAttachment } from '@/types/api'
import { formatBytes } from '@/utils/format'

/*
 * 看图。侧栏只有 288px，截图在那个宽度下等于看不清，所以点缩略图要能放大。
 *
 * 附件列表由调用方传进来，这个弹框自己不拉接口：它只在「已经有一份列表」时被打开，
 * 再拉一次会让点开之后图片位先空一下，且两份列表可能不一致（比如刚删过一张）。
 */
const props = withDefaults(
  defineProps<{ attachments: ImageAttachment[]; selectedId?: number | null }>(),
  { selectedId: null },
)

const open = defineModel<boolean>('open', { default: false })
const selectedId = defineModel<number | null>('selectedId', { default: null })

// 选中的那张可能已经不在列表里（换了任务、列表重拉过），那就退回第一张，而不是空着
const current = computed(
  () =>
    props.attachments.find((item) => item.id === selectedId.value) ?? props.attachments[0] ?? null,
)

const broken = ref(false)
watch(
  () => current.value?.id,
  () => {
    broken.value = false
  },
)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="log"
    :title="current?.filename ?? '图片'"
    :description="current ? `${formatBytes(current.sizeBytes)} · 原图，点右下角可以存下来` : ''"
  >
    <div v-if="current" class="flex flex-col gap-2">
      <div class="flex max-h-[60vh] min-h-32 items-center justify-center overflow-auto rounded-panel bg-content">
        <img
          v-if="!broken"
          :src="current.contentUrl"
          :alt="current.filename"
          class="max-h-[60vh] max-w-full object-contain"
          @error="broken = true"
        />
        <p v-else class="p-6 text-caption text-label-tertiary">
          图片读不出来 —— 文件可能已被清理，或后端刚重启过。
        </p>
      </div>

      <!-- 只有一张时不给缩略图条：一条「只有自己」的选择器不产生任何信息 -->
      <ImageAttachmentGrid
        v-if="attachments.length > 1"
        :attachments="attachments"
        size="sm"
        @select="selectedId = $event.id"
      />
    </div>

    <template #footer>
      <Button @click="open = false">关闭</Button>
      <a
        v-if="current"
        :href="current.contentUrl"
        :download="current.filename"
        :class="buttonVariants({ variant: 'primary' })"
      >
        下载原图
      </a>
    </template>
  </Dialog>
</template>

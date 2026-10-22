<script setup lang="ts">
import { Check, Copy } from '@lucide/vue'
import { computed, onUnmounted, ref } from 'vue'

import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { copyTextToClipboard } from '@/utils/clipboard'

const props = withDefaults(
  defineProps<{
    text: string
    label: string
    successMessage: string
    size?: 'sm' | 'regular' | 'lg'
  }>(),
  { size: 'sm' },
)

const toast = useToast()
const copied = ref(false)
let copiedTimer: number | undefined
const disabled = computed(() => props.text.length === 0)

async function copy() {
  if (disabled.value) return

  if (!(await copyTextToClipboard(props.text))) {
    toast.warning(`${props.label}失败`, '可以手动选中内容复制')
    return
  }

  copied.value = true
  window.clearTimeout(copiedTimer)
  copiedTimer = window.setTimeout(() => {
    copied.value = false
  }, 1600)
  toast.success(props.successMessage)
}

onUnmounted(() => window.clearTimeout(copiedTimer))
</script>

<template>
  <Tooltip :content="copied ? '已复制' : label">
    <IconButton
      :label="copied ? '已复制' : label"
      :size="size"
      :disabled="disabled"
      @click.stop="copy"
    >
      <Icon :as="copied ? Check : Copy" size="inline" />
    </IconButton>
  </Tooltip>
</template>

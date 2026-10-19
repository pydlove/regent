<script setup lang="ts">
import { computed } from 'vue'

import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import type { DeploymentSummary } from '@/types/api'

const props = withDefaults(
  defineProps<{ deployment: DeploymentSummary | null; starting?: boolean }>(),
  { starting: false },
)
const emit = defineEmits<{ confirm: [] }>()
const open = defineModel<boolean>('open', { default: false })
const authHint = computed(() =>
  props.deployment?.authType === 'PASSWORD'
    ? '将使用配置时保存的 SSH 密码连接，不再重复输入。'
    : '将使用本机 SSH 密钥或 ssh-agent 连接。',
)
</script>

<template>
  <Dialog
    v-model:open="open"
    title="确认执行部署"
    :description="deployment ? `即将在「${deployment.name}」上执行部署脚本` : ''"
    icon-tone="default"
  >
    <p class="text-body text-label-secondary">{{ authHint }}</p>
    <p class="mt-2 text-caption text-label-tertiary">
      执行会连接远程服务器并运行脚本，请确认目标和脚本均无误。
    </p>
    <template #footer>
      <Button :disabled="starting" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="starting" @click="emit('confirm')">
        {{ starting ? '连接中…' : '确认执行' }}
      </Button>
    </template>
  </Dialog>
</template>

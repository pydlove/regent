<script setup lang="ts">
import { CornerDownRight, GitBranch } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { createTaskContinuation } from '@/api/tasks'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import type { TaskDetail } from '@/types/api'

const props = defineProps<{
  task: TaskDetail | null
}>()

const emit = defineEmits<{
  created: [task: TaskDetail]
}>()

const open = defineModel<boolean>('open', { default: false })
const prompt = ref('')
const priority = ref('0')
const saving = ref(false)
const error = ref<string | null>(null)

const canSubmit = computed(() => !saving.value && prompt.value.trim().length > 0)

watch(open, (value) => {
  if (!value) return
  prompt.value = ''
  priority.value = String(props.task?.priority ?? 0)
  saving.value = false
  error.value = null
})

const submit = async () => {
  const task = props.task
  if (!task || !canSubmit.value) return

  saving.value = true
  error.value = null
  try {
    const created = await createTaskContinuation(task.id, {
      prompt: prompt.value.trim(),
      priority: Number(priority.value) || 0,
    })
    emit('created', created)
    open.value = false
  } catch (cause) {
    error.value = cause instanceof ApiError
      ? cause.message
      : '后续任务没有创建成功，请稍后重试'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="继续创建任务"
    :description="task ? `新任务会复用 #${task.id} 的 Codex 会话与分支` : ''"
    :icon="CornerDownRight"
  >
    <div v-if="task" class="flex flex-col gap-4">
      <div class="rounded-panel border border-accent/20 bg-accent/6 px-3 py-3">
        <p class="text-body font-emphasis text-label">{{ task.title }}</p>
        <p class="tnum mt-1 text-caption text-label-tertiary">
          #{{ task.id }} · Codex 会话已建立
        </p>
        <p v-if="task.branchName" class="mt-1 flex items-center gap-1 text-caption text-label-secondary">
          <Icon :as="GitBranch" size="inline" />
          <span class="break-all font-mono">{{ task.branchName }}</span>
        </p>
        <p class="mt-2 text-caption leading-5 text-label-secondary">
          新任务会沿用已有上下文，不会从头开始。为避免同一个会话被并发写入，
          来源任务完成、失败或终止后才能派发新任务。
        </p>
      </div>

      <DialogField label="新的开发内容" hint="只写这次要继续补充或修改的部分，不需要重抄来源任务">
        <Textarea
          v-model="prompt"
          :rows="9"
          :maxlength="12000"
          :disabled="saving"
          placeholder="例如：在现有实现上补齐权限校验、异常状态和对应测试"
        />
        <p class="tnum mt-1 text-right text-caption text-label-tertiary">
          {{ prompt.length }} / 12000
        </p>
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发">
        <div class="w-24">
          <Input v-model="priority" type="number" :disabled="saving" />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSubmit" @click="submit">
        {{ saving ? '创建中…' : '创建后续任务' }}
      </Button>
    </template>
  </Dialog>
</template>

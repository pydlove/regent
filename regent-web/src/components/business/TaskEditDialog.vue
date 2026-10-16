<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import { useToast } from '@/composables/useToast'
import { useTaskStore } from '@/stores/useTaskStore'
import type { TaskDetail } from '@/types/api'

const props = defineProps<{
  task: TaskDetail | null
}>()

const open = defineModel<boolean>('open', { default: false })
const store = useTaskStore()
const toast = useToast()

const prompt = ref('')
const priority = ref('0')
const error = ref<string | null>(null)
const saving = ref(false)

watch(open, (value) => {
  if (!value || !props.task) return
  prompt.value = props.task.prompt
  priority.value = String(props.task.priority)
  error.value = null
  saving.value = false
})

const changed = computed(
  () =>
    props.task !== null &&
    (prompt.value.trim() !== props.task.prompt || Number(priority.value) !== props.task.priority),
)

const canSave = computed(
  () => !saving.value && prompt.value.trim().length > 0 && changed.value,
)

const save = async () => {
  const task = props.task
  if (!task || !canSave.value) return

  saving.value = true
  error.value = null
  const failure = await store.edit(task.id, {
    prompt: prompt.value.trim(),
    priority: Number(priority.value) || 0,
  })
  saving.value = false

  if (failure) {
    error.value = failure
    return
  }

  open.value = false
  toast.success(`#${task.id} 已更新`, '标题已按新的指令首行重新生成')
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="编辑任务"
    :description="task ? `#${task.id} 的指令与优先级` : ''"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="指令" hint="第一行会被拿去当任务标题，修改后会同步更新">
        <Textarea
          v-model="prompt"
          :rows="10"
          :disabled="saving"
          placeholder="交给执行器的完整指令：要改哪里、验收标准是什么"
        />
      </DialogField>

      <DialogField label="优先级" hint="数值越大越先派发">
        <div class="w-20">
          <Input v-model="priority" type="number" :disabled="saving" />
        </div>
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存修改' }}
      </Button>
    </template>
  </Dialog>
</template>

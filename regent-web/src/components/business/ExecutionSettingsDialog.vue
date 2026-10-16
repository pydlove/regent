<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { getExecutionSettings, updateExecutionSettings } from '@/api/settings'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import { useToast } from '@/composables/useToast'

const open = defineModel<boolean>('open', { default: false })

const toast = useToast()
const limit = ref('1')
const runningTaskCount = ref(0)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const numericLimit = computed(() => Number(limit.value))
const valid = computed(
  () => Number.isInteger(numericLimit.value) && numericLimit.value >= 1 && numericLimit.value <= 64,
)

const load = async () => {
  loading.value = true
  error.value = null
  try {
    const settings = await getExecutionSettings()
    limit.value = String(settings.maxConcurrentTasks)
    runningTaskCount.value = settings.runningTaskCount
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法读取执行设置'
  } finally {
    loading.value = false
  }
}

watch(open, (value) => {
  if (value) void load()
})

const save = async () => {
  if (!valid.value || saving.value) return
  saving.value = true
  error.value = null
  try {
    const settings = await updateExecutionSettings(numericLimit.value)
    limit.value = String(settings.maxConcurrentTasks)
    runningTaskCount.value = settings.runningTaskCount
    toast.success('执行设置已保存', `新的并发上限为 ${settings.maxConcurrentTasks}`)
    open.value = false
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '无法保存执行设置'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    title="执行设置"
    description="控制平台同时运行的任务数量。"
  >
    <div class="flex flex-col gap-4">
      <DialogField
        label="并发上限"
        hint="范围 1–64。调低不会终止正在运行的任务，新任务会等待有空位。"
      >
        <div class="flex items-center gap-2">
          <Input
            v-model="limit"
            type="number"
            placeholder="1"
            :disabled="loading || saving"
            :invalid="!valid"
          />
          <span class="shrink-0 text-caption text-label-secondary">个任务</span>
        </div>
      </DialogField>

      <div class="flex items-center rounded-panel border border-separator-soft bg-content px-3 py-2">
        <span class="text-caption text-label-secondary">当前运行</span>
        <span class="tnum ml-auto text-body font-emphasis text-label">
          {{ loading ? '—' : runningTaskCount }} / {{ valid ? numericLimit : '—' }}
        </span>
      </div>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="loading || saving || !valid" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </Button>
    </template>
  </Dialog>
</template>

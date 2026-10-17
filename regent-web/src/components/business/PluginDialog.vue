<script setup lang="ts">
import { FileCode2, Upload } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import {
  createPlugin,
  getPlugin,
  updatePlugin,
  type SavePluginInput,
} from '@/api/plugins'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import Switch from '@/components/ui/switch/Switch.vue'
import { useToast } from '@/composables/useToast'
import type { PluginDetail, PluginSummary } from '@/types/api'
import { formatBytes } from '@/utils/format'

const MAX_CONTENT_BYTES = 256 * 1024

const props = defineProps<{ plugin: PluginSummary | null }>()
const emit = defineEmits<{ saved: [plugin: PluginDetail] }>()
const open = defineModel<boolean>('open', { default: false })
const toast = useToast()

const name = ref('')
const description = ref('')
const tagsText = ref('')
const filename = ref('capability.txt')
const content = ref('')
const enabled = ref(true)
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const readonly = computed(() => props.plugin?.builtin === true)

const canSave = computed(
  () =>
    !readonly.value &&
    name.value.trim().length > 0 &&
    filename.value.trim().length > 0 &&
    content.value.trim().length > 0 &&
    content.value.length <= MAX_CONTENT_BYTES &&
    !saving.value,
)

function messageOf(e: unknown): string {
  if (e instanceof ApiError) return e.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

function parsedTags(): string[] {
  return tagsText.value
    .split(',')
    .map((tag) => tag.trim())
    .filter((tag, index, tags) => tag.length > 0 && tags.indexOf(tag) === index)
}

watch(open, async (opened) => {
  if (!opened) return
  error.value = null
  saving.value = false
  loading.value = props.plugin !== null
  name.value = props.plugin?.name ?? ''
  description.value = props.plugin?.description ?? ''
  tagsText.value = props.plugin?.tags.join(', ') ?? ''
  filename.value = props.plugin?.filename ?? 'capability.txt'
  content.value = ''
  enabled.value = props.plugin?.enabled ?? true
  if (!props.plugin) return
  try {
    const detail = await getPlugin(props.plugin.id)
    content.value = detail.content
  } catch (e) {
    error.value = messageOf(e)
  } finally {
    loading.value = false
  }
})

const importFile = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > MAX_CONTENT_BYTES) {
    toast.warning(
      `「${file.name}」太大（${formatBytes(file.size)}）`,
      `单个插件内容上限 ${formatBytes(MAX_CONTENT_BYTES)}`,
    )
    return
  }
  try {
    content.value = await file.text()
    filename.value = file.name
    if (!name.value.trim()) {
      name.value = file.name.replace(/\.[^.]+$/, '')
    }
  } catch {
    toast.warning('无法读取文件', '请确认文件是 UTF-8 文本或代码文件')
  }
}

const save = async () => {
  if (!canSave.value || readonly.value) return
  saving.value = true
  error.value = null
  const input: SavePluginInput = {
    name: name.value.trim(),
    description: description.value.trim(),
    tags: parsedTags(),
    filename: filename.value.trim(),
    content: content.value,
    enabled: enabled.value,
  }
  try {
    const saved = props.plugin
      ? await updatePlugin(props.plugin.id, input)
      : await createPlugin(input)
    open.value = false
    emit('saved', saved)
    toast.success(
      props.plugin ? `插件「${saved.name}」已更新到 v${saved.currentRevision}` : `插件「${saved.name}」已创建`,
      props.plugin ? '已绑定任务仍固定使用原版本' : '创建任务时可以绑定这个能力包',
    )
  } catch (e) {
    error.value = messageOf(e)
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
    :title="readonly ? '查看内置插件' : props.plugin ? '编辑插件' : '新增插件'"
    :description="
      readonly
        ? '内置插件随发行包更新，只能启用或停用，不能手工修改正文。'
        : '把可复用代码、协议说明或接入步骤存成能力包；执行任务时交给 AI 直接复用。'
    "
  >
    <div class="flex flex-col gap-3">
      <p v-if="loading" class="text-caption text-label-tertiary">正在读取插件内容…</p>

      <DialogField label="名称" required hint="例如：Kafka 读取组件">
        <Input v-model="name" :readonly="readonly" placeholder="Kafka 读取组件" class="w-full" />
      </DialogField>

      <DialogField label="说明" hint="说明适用场景、依赖和限制">
        <Textarea
          v-model="description"
          :readonly="readonly"
          :rows="2"
          placeholder="复用已有消费者配置与序列化处理"
        />
      </DialogField>

      <DialogField label="标签" hint="用逗号分隔，最多 8 个">
        <Input
          v-model="tagsText"
          :readonly="readonly"
          placeholder="Kafka, 中间件, Java"
          class="w-full"
        />
      </DialogField>

      <DialogField label="文件名" required hint="导入文件时自动填入，也可以手动修改">
        <div class="flex items-center gap-2">
          <Input
            v-model="filename"
            :readonly="readonly"
            placeholder="KafkaReader.java"
            class="min-w-0 flex-1"
          />
          <Button v-if="!readonly" :disabled="loading || saving" @click="fileInput?.click()">
            <Icon :as="Upload" size="inline" />从文件导入
          </Button>
          <input ref="fileInput" type="file" class="hidden" @change="importFile" />
        </div>
      </DialogField>

      <DialogField label="内容" required :hint="`UTF-8 文本或代码，最大 ${formatBytes(MAX_CONTENT_BYTES)}`">
        <Textarea
          v-model="content"
          :readonly="readonly"
          :rows="14"
          class="font-mono"
          placeholder="粘贴已经验证过的代码或实现说明，不要放密码、Token 等凭据"
        />
      </DialogField>

      <DialogField label="状态">
        <Switch v-model="enabled" :disabled="readonly" label="启用，可被任务绑定" />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">{{ readonly ? '关闭' : '取消' }}</Button>
      <Button v-if="!readonly" variant="primary" :disabled="!canSave" @click="save">
        <Icon :as="FileCode2" size="inline" />
        {{ saving ? '保存中…' : '保存插件' }}
      </Button>
    </template>
  </Dialog>
</template>

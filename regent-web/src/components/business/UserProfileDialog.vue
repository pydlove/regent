<script setup lang="ts">
import { ImagePlus, X } from '@lucide/vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES } from '@/api/attachments'
import { ApiError } from '@/api/http'
import Avatar from '@/components/ui/avatar/Avatar.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import { useToast } from '@/composables/useToast'
import { useAuthStore } from '@/stores/useAuthStore'
import { formatBytes } from '@/utils/format'

const open = defineModel<boolean>('open', { default: false })
const auth = useAuthStore()
const toast = useToast()

const displayName = ref('')
const selectedAvatar = ref<File | null>(null)
const avatarPreview = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const saving = ref(false)
const error = ref<string | null>(null)

const normalizedName = computed(() => displayName.value.trim())
const nameValid = computed(
  () => normalizedName.value.length > 0 &&
    Array.from(normalizedName.value).length <= 32 &&
    !/[\u0000-\u001f\u007f]/.test(normalizedName.value),
)
const changed = computed(
  () =>
    normalizedName.value !== auth.user?.displayName ||
    selectedAvatar.value !== null,
)
const canSave = computed(() => nameValid.value && changed.value && !saving.value)
const previewSource = computed(() => avatarPreview.value || auth.user?.avatarUrl || '')

function releasePreview() {
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value)
  avatarPreview.value = ''
}

function resetAvatar() {
  releasePreview()
  selectedAvatar.value = null
  if (fileInput.value) fileInput.value.value = ''
}

watch(open, (value) => {
  if (!value) {
    resetAvatar()
    return
  }
  displayName.value = auth.user?.displayName || auth.user?.username || ''
  error.value = null
  saving.value = false
  resetAvatar()
})

const pickAvatar = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
    error.value = '头像只支持 PNG / JPEG / GIF / WebP'
    return
  }
  if (file.size > MAX_IMAGE_BYTES) {
    error.value = `头像不能超过 ${formatBytes(MAX_IMAGE_BYTES)}`
    return
  }
  releasePreview()
  selectedAvatar.value = file
  avatarPreview.value = URL.createObjectURL(file)
  error.value = null
}

const save = async () => {
  if (!canSave.value) return
  saving.value = true
  error.value = null
  try {
    await auth.saveProfile(normalizedName.value, selectedAvatar.value)
    open.value = false
    toast.success('个人资料已更新')
  } catch (cause) {
    error.value = cause instanceof ApiError ? cause.message : '资料保存失败，请稍后重试'
  } finally {
    saving.value = false
  }
}

onUnmounted(releasePreview)
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    title="个人资料"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="头像">
        <div class="flex items-center gap-3">
          <Avatar :src="previewSource" :name="normalizedName || auth.user?.username || 'U'" size="profile" />
          <div class="flex items-center gap-2">
            <Button :disabled="saving" @click="fileInput?.click()">
              <Icon :as="ImagePlus" size="inline" />
              {{ selectedAvatar ? '更换' : '上传头像' }}
            </Button>
            <Button v-if="selectedAvatar" variant="ghost" :disabled="saving" @click="resetAvatar">
              <Icon :as="X" size="inline" />
              撤销
            </Button>
          </div>
        </div>
        <input
          ref="fileInput"
          type="file"
          :accept="ACCEPTED_IMAGE_TYPES.join(',')"
          class="hidden"
          @change="pickAvatar"
        />
      </DialogField>

      <DialogField label="名称" required>
        <Input v-model="displayName" :disabled="saving" :invalid="displayName.length > 0 && !nameValid" />
      </DialogField>

      <DialogField label="账号">
        <Input :model-value="auth.user?.username ?? ''" disabled />
      </DialogField>

      <DialogField v-if="auth.user?.email" label="邮箱">
        <Input :model-value="auth.user.email" disabled />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <template #footer>
      <Button :disabled="saving" @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave" @click="save">
        {{ saving ? '保存中…' : '保存' }}
      </Button>
    </template>
  </Dialog>
</template>

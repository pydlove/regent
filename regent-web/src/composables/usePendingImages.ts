import { computed, onUnmounted, ref } from 'vue'

import { ACCEPTED_IMAGE_TYPES, MAX_IMAGE_BYTES } from '@/api/attachments'
import { useToast } from '@/composables/useToast'
import { formatBytes } from '@/utils/format'

export type PendingImage = {
  localId: number
  file: File
  /** 提交完或组件卸载时必须 revoke，否则 blob 会一直占着内存。 */
  previewUrl: string
  progress: number
  status: 'idle' | 'uploading' | 'done' | 'failed'
  error: string | null
}

type UploadImage = (file: File, onProgress: (percent: number) => void) => Promise<unknown>

/** 弹框里的待上传图片：选文件、拖放、粘贴三种入口共用同一份状态。 */
export function usePendingImages() {
  const toast = useToast()
  const pending = ref<PendingImage[]>([])
  const dragging = ref(false)
  const fileInput = ref<HTMLInputElement | null>(null)
  let nextLocalId = 1

  const failed = computed(() => pending.value.filter((item) => item.status === 'failed'))
  const pendingBytes = computed(() => pending.value.reduce((sum, item) => sum + item.file.size, 0))

  const releasePreviews = () => {
    for (const item of pending.value) URL.revokeObjectURL(item.previewUrl)
    pending.value = []
  }

  const reset = () => {
    dragging.value = false
    releasePreviews()
  }

  const addFiles = (files: File[]) => {
    for (const file of files) {
      if (!ACCEPTED_IMAGE_TYPES.includes(file.type)) {
        toast.warning(`「${file.name}」不是支持的图片`, '只收 PNG / JPEG / GIF / WebP 四种')
        continue
      }
      if (file.size > MAX_IMAGE_BYTES) {
        toast.warning(
          `「${file.name}」太大（${formatBytes(file.size)}）`,
          `单张上限 ${formatBytes(MAX_IMAGE_BYTES)}，压缩后再试`,
        )
        continue
      }
      if (
        pending.value.some(
          (item) => item.file.name === file.name && item.file.size === file.size,
        )
      ) {
        toast.show(`「${file.name}」已经在列表里了`)
        continue
      }
      pending.value.push({
        localId: nextLocalId++,
        file,
        previewUrl: URL.createObjectURL(file),
        progress: 0,
        status: 'idle',
        error: null,
      })
    }
  }

  const onPick = (event: Event) => {
    const input = event.target as HTMLInputElement
    addFiles(Array.from(input.files ?? []))
    // 连着选同一个文件两次时 change 不会再触发，清空 value 才能再次选择。
    input.value = ''
  }

  const onPaste = (event: ClipboardEvent) => {
    const data = event.clipboardData
    if (!data) return
    const files = Array.from(data.items)
      .filter((item) => item.kind === 'file' && item.type.startsWith('image/'))
      .map((item) => item.getAsFile())
      .filter((file): file is File => file !== null)
    if (files.length === 0) return
    addFiles(files)
    // 富文本里文字和图同时存在时，让浏览器继续插入文字，同时把图片加入待上传列表。
    if (data.getData('text/plain').length > 0) return
    event.preventDefault()
  }

  const onDrop = (event: DragEvent) => {
    dragging.value = false
    const files = Array.from(event.dataTransfer?.files ?? [])
    if (files.length > 0) addFiles(files)
  }

  const onDragLeave = (event: DragEvent) => {
    const next = event.relatedTarget as Node | null
    if (!next || !(event.currentTarget as Node).contains(next)) dragging.value = false
  }

  const removeImage = (item: PendingImage) => {
    if (item.status === 'uploading') return
    URL.revokeObjectURL(item.previewUrl)
    pending.value = pending.value.filter((entry) => entry.localId !== item.localId)
  }

  /** 顺序上传，避免多张图的进度彼此抢带宽：一并发看起来更像卡住。 */
  const uploadAll = async (
    upload: UploadImage,
    images: readonly PendingImage[] = pending.value,
  ) => {
    let uploaded = 0
    for (const item of images) {
      if (item.status === 'done') continue
      item.status = 'uploading'
      item.progress = 0
      item.error = null
      try {
        await upload(item.file, (percent) => {
          item.progress = percent
        })
        item.status = 'done'
        item.progress = 100
        uploaded++
      } catch (error) {
        item.status = 'failed'
        item.error = error instanceof Error ? error.message : '上传失败'
      }
    }
    return uploaded
  }

  onUnmounted(releasePreviews)

  return {
    pending,
    dragging,
    fileInput,
    failed,
    pendingBytes,
    addFiles,
    onPick,
    onPaste,
    onDrop,
    onDragLeave,
    removeImage,
    reset,
    uploadAll,
  }
}

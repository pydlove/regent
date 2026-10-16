<script setup lang="ts">
import { ArrowUp, Folder } from '@lucide/vue'
import { computed, nextTick, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { listDirectories } from '@/api/projects'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import type { DirectoryListing } from '@/types/api'

/*
 * 选文件夹。
 *
 * 为什么让后端列目录，而不是用浏览器自带的目录选择：<input type="file" webkitdirectory>
 * 出于安全**拿不到绝对路径**，只给目录名和相对路径，而注册项目要的正是 codex 的工作目录
 * 绝对路径。后端与 codex 同机，它看到的就是执行时的目录树 —— 所以这里列出来的目录，
 * 都是真的能跑起来的那种。
 *
 * 交互照 NSOpenPanel：位置条在最上（「上一级」贴左），下面一列子目录，单击选中、双击进入；
 * 主按钮选的是**当前所在目录**，选中某一行时它变成「选择「名字」」—— 和 macOS 一样，
 * 选中了谁就选谁。方向键与回车也管用：挑目录是个来回翻的过程，不该逼人抓鼠标。
 *
 * 只列子目录、不列以 . 开头的隐藏目录（Finder 的默认）：选择器的结果会被拿去当工作目录，
 * 而 .git / .venv 这类目录不是工作区。
 */
const props = defineProps<{
  /** 打开时落在哪个目录。空或读不到就回到主目录，并把原因写在提示里 */
  initial?: string
}>()

const emit = defineEmits<{ picked: [path: string] }>()

const open = defineModel<boolean>('open', { default: false })

const listing = ref<DirectoryListing | null>(null)
const selected = ref<string | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const fallbackNote = ref<string | null>(null)
const pathBar = ref<HTMLElement | null>(null)
const listEl = ref<HTMLElement | null>(null)

const describe = (e: unknown) =>
  e instanceof ApiError ? e.message : '读不到目录，请确认后端已启动（默认 127.0.0.1:8787）'

const read = async (path?: string) => {
  listing.value = await listDirectories(path)
  selected.value = null
  // 路径比位置条长时，露出的是尾巴 —— 认路靠的是最后几段，不是开头的 /Users
  await nextTick()
  if (pathBar.value) pathBar.value.scrollLeft = pathBar.value.scrollWidth
}

const load = async (path?: string) => {
  loading.value = true
  error.value = null
  fallbackNote.value = null
  try {
    await read(path)
  } catch (e) {
    const message = describe(e)
    // 打开时带的路径读不到（手打的路径不存在、或者刚被删掉）：退回主目录，并把原因说出来。
    // 直接卡在报错上，人就只能关掉弹框想办法 —— 那等于这个选择器没帮上忙
    const recovered = path ? await read().then(() => true).catch(() => false) : false
    if (recovered) fallbackNote.value = `${path} 读不到：${message}。已回到主目录`
    else error.value = message
  } finally {
    loading.value = false
  }
}

watch(open, (value) => {
  if (value) void load(props.initial?.trim() || undefined)
})

const entries = computed(() => listing.value?.entries ?? [])
const selectedEntry = computed(
  () => entries.value.find((entry) => entry.path === selected.value) ?? null,
)

const primaryLabel = computed(() =>
  selectedEntry.value ? `选择「${selectedEntry.value.name}」` : '选择此文件夹',
)

const goUp = () => {
  const parent = listing.value?.parent
  if (parent) void load(parent)
}

const enter = (path: string) => void load(path)

const pick = () => {
  const path = selectedEntry.value?.path ?? listing.value?.path
  if (!path) return
  emit('picked', path)
  open.value = false
}

/** 方向键在列表里挪，回车确认。挪出可视区要跟着滚动，否则人不知道选到哪了 */
const move = (delta: number) => {
  if (entries.value.length === 0) return
  const current = entries.value.findIndex((entry) => entry.path === selected.value)
  const next = current < 0
    ? (delta > 0 ? 0 : entries.value.length - 1)
    : Math.min(Math.max(current + delta, 0), entries.value.length - 1)
  selected.value = entries.value[next].path
  void nextTick(() => {
    listEl.value?.querySelector('[aria-selected="true"]')?.scrollIntoView({ block: 'nearest' })
  })
}

const onListKeydown = (event: KeyboardEvent) => {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    move(1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    move(-1)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    pick()
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="选择文件夹"
    description="从本机已有的目录里挑一个 —— 后端和 codex 跑在同一台机器上，这里看到的就是执行时的工作目录。"
  >
    <div class="flex flex-col gap-3">
      <!-- 位置条。「上一级」贴左，和 Finder 的工具栏一样 -->
      <div
        class="flex items-center gap-2 rounded-panel border border-separator-soft bg-content py-1 pl-1 pr-2"
      >
        <IconButton label="上一级" size="sm" :disabled="!listing?.parent || loading" @click="goUp">
          <Icon :as="ArrowUp" size="inline" />
        </IconButton>
        <span ref="pathBar" class="scroll-area min-w-0 flex-1 overflow-x-auto whitespace-nowrap font-mono text-caption text-label">
          {{ listing?.path ?? '正在读取…' }}
        </span>
      </div>

      <!-- 列表本身就是焦点区：打开弹框后方向键直接可用，不用先点一下 -->
      <div
        ref="listEl"
        role="listbox"
        aria-label="子目录"
        tabindex="0"
        class="max-h-72 min-h-40 overflow-y-auto rounded-panel border border-separator-soft bg-content p-1 outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
        @keydown="onListKeydown"
      >
        <div
          v-for="entry in entries"
          :key="entry.path"
          role="option"
          :aria-selected="entry.path === selected"
          class="group flex h-row cursor-default select-none items-center gap-2 rounded-control px-2 text-body"
          :class="entry.path === selected ? 'bg-accent text-white' : 'text-label hover:bg-hover'"
          @click="selected = entry.path"
          @dblclick="enter(entry.path)"
        >
          <Icon :as="Folder" size="inline" class="text-label-secondary group-aria-[selected=true]:text-white" />
          <span class="truncate">{{ entry.name }}</span>
        </div>

        <p v-if="loading" class="py-8 text-center text-caption text-label-tertiary">正在读取…</p>
        <p v-else-if="error" class="py-8 text-center text-caption text-danger">{{ error }}</p>
        <p v-else-if="entries.length === 0" class="py-8 text-center text-caption text-label-tertiary">
          这个目录下没有子目录
        </p>
      </div>

      <p v-if="fallbackNote" class="text-caption text-warning">{{ fallbackNote }}</p>
      <p v-else class="text-caption text-label-tertiary">
        单击选中、双击进入；只列子目录，以 . 开头的隐藏目录不显示<template v-if="listing?.truncated">。子目录太多，只显示了前 500 个</template>
      </p>
    </div>

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!listing || loading" @click="pick">{{ primaryLabel }}</Button>
    </template>
  </Dialog>
</template>

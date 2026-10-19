<script setup lang="ts">
import { ChevronLeft, ImagePlus, Plus, ShieldCheck, Wrench } from '@lucide/vue'
import { computed, onMounted, ref, watch } from 'vue'

import { ACCEPTED_IMAGE_TYPES, listBugAttachments, uploadBugAttachment } from '@/api/attachments'
import { ApiError } from '@/api/http'
import BugCreateDialog from '@/components/business/BugCreateDialog.vue'
import BugSeverityBadge from '@/components/business/BugSeverityBadge.vue'
import BugStatusBadge from '@/components/business/BugStatusBadge.vue'
import ImageAttachmentGrid from '@/components/business/ImageAttachmentGrid.vue'
import ImageAttachmentsDialog from '@/components/business/ImageAttachmentsDialog.vue'
import WorkItemSwitcher from '@/components/business/WorkItemSwitcher.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import IconButton from '@/components/ui/icon-button/IconButton.vue'
import Input from '@/components/ui/input/Input.vue'
import ListToolbar from '@/components/ui/list-toolbar/ListToolbar.vue'
import Pagination from '@/components/ui/pagination/Pagination.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import SegmentedControl from '@/components/ui/segmented-control/SegmentedControl.vue'
import Table from '@/components/ui/table/Table.vue'
import type { TableColumn } from '@/components/ui/table/table.types'
import Tooltip from '@/components/ui/tooltip/Tooltip.vue'
import { useToast } from '@/composables/useToast'
import { useBugStore } from '@/stores/useBugStore'
import type { BugAttachment, ImageAttachment } from '@/types/api'
import {
  canMarkFixed,
  canRequestFix,
  canVerify,
  canWontFix,
  type BugSeverity,
  type BugStatus,
} from '@/types/bug'
import { formatRelative } from '@/utils/format'

/* 缺陷单按录入先后排序：先添加的在前，分页也沿这条顺序切。 */

const bugStore = useBugStore()
const toast = useToast()

const columns: TableColumn[] = [
  { key: 'id', label: '#', width: '56px', numeric: true },
  { key: 'title', label: '缺陷' },
  { key: 'severity', label: '严重级', width: '88px' },
  { key: 'status', label: '状态', width: '96px' },
  { key: 'source', label: '来源', width: '104px', hideBelow: 'md' },
  { key: 'foundAt', label: '发现时间', align: 'right', width: '88px', hideBelow: 'md' },
]

const scopeOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'OPEN', label: '未解决' },
  { value: 'RESOLVED', label: '已解决' },
]

const selectedId = ref<number | null>(null)
const selected = computed(() => bugStore.bugs.find((b) => b.id === selectedId.value) ?? null)

const attachments = ref<BugAttachment[]>([])
const attachmentError = ref<string | null>(null)
const attaching = ref(false)
const requestingFix = ref(false)
const changingStatus = ref(false)
const attachInput = ref<HTMLInputElement | null>(null)
const viewerOpen = ref(false)
const viewerId = ref<number | null>(null)

const taskLabel = (id: number | null) => (id === null ? '人工录入' : `#${id}`)

function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message
  return '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
}

const loadAttachments = async (id: number) => {
  attachmentError.value = null
  try {
    attachments.value = await listBugAttachments(id)
  } catch (error) {
    attachments.value = []
    attachmentError.value = messageOf(error)
  }
}

watch(selectedId, (id) => {
  attachments.value = []
  attachmentError.value = null
  viewerOpen.value = false
  viewerId.value = null
  if (id !== null) void loadAttachments(id)
})

const openViewer = (attachment: ImageAttachment) => {
  viewerId.value = attachment.id
  viewerOpen.value = true
}

const attachFiles = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  input.value = ''
  const bugId = selectedId.value
  if (bugId === null || files.length === 0) return

  attaching.value = true
  attachmentError.value = null
  let uploaded = 0
  try {
    for (const file of files) {
      await uploadBugAttachment(bugId, file)
      uploaded++
    }
    await loadAttachments(bugId)
    toast.success(`${uploaded} 张图片已附上`, `Bug #${bugId} 的复现证据已保存`)
  } catch (error) {
    if (uploaded > 0) await loadAttachments(bugId)
    attachmentError.value = messageOf(error)
  } finally {
    attaching.value = false
  }
}

const requestFix = async () => {
  const bug = selected.value
  if (!bug || requestingFix.value) return

  requestingFix.value = true
  try {
    const updated = await bugStore.requestFix(bug.id)
    toast.success(
      `修复任务 #${updated.fixTaskId} 已派发`,
      '执行位够就立即起跑，否则先按顺序排队',
    )
  } catch (error) {
    toast.warning(`Bug #${bug.id} 派发修复任务被拒绝`, messageOf(error))
  } finally {
    requestingFix.value = false
  }
}

const markFixed = async () => {
  const bug = selected.value
  if (!bug || changingStatus.value) return

  changingStatus.value = true
  try {
    await bugStore.markFixed(bug.id)
    toast.success(`Bug #${bug.id} 已标记修复`, '还需要人工验证，验证通过后再关闭')
  } catch (error) {
    toast.warning(`Bug #${bug.id} 无法标记为已修复`, messageOf(error))
  } finally {
    changingStatus.value = false
  }
}

const verify = async () => {
  const bug = selected.value
  if (!bug || changingStatus.value) return

  changingStatus.value = true
  try {
    await bugStore.verify(bug.id)
    toast.success(`Bug #${bug.id} 已验证关闭`)
  } catch (error) {
    toast.warning(`Bug #${bug.id} 无法验证关闭`, messageOf(error))
  } finally {
    changingStatus.value = false
  }
}

const wontFixOpen = ref(false)
const wontFixReason = ref('')
const WONTFIX_MIN = 6
const canWontFixConfirm = computed(() => wontFixReason.value.trim().length >= WONTFIX_MIN)

const openWontFix = () => {
  wontFixReason.value = ''
  wontFixOpen.value = true
}

const confirmWontFix = async () => {
  const bug = selected.value
  if (!bug || !canWontFixConfirm.value || changingStatus.value) return

  changingStatus.value = true
  try {
    await bugStore.wontFix(bug.id, wontFixReason.value.trim())
    wontFixOpen.value = false
    toast.success(`Bug #${bug.id} 已标记为不修复`, '理由已经保留在缺陷单里')
  } catch (error) {
    toast.warning(`Bug #${bug.id} 无法标记为不修复`, messageOf(error))
  } finally {
    changingStatus.value = false
  }
}

// 列表里已经有严重级和状态两列，详情面板不再重复一遍大标题，只补上下文
const context: Record<BugStatus, string> = {
  OPEN: '还没派人修。派发修复任务后会从执行器那拿一条带上下文的任务。',
  FIXING: '修复任务在执行中。跑完之后要人验一遍才能关。',
  FIXED: '修复任务已跑完，但没人验证过。别把它当已经好了。',
  CLOSED: '已验证关闭。',
  WONTFIX: '已判定不修。',
}

const createOpen = ref(false)

onMounted(() => {
  void bugStore.load()
  void bugStore.loadOpenCount()
})
</script>

<template>
  <div class="flex h-full min-h-0">
    <div class="flex min-w-0 flex-1 flex-col">
      <ListToolbar>
        <template #title>
          <h1 class="text-body font-emphasis text-label">Bug 单</h1>
          <WorkItemSwitcher value="BUGS" />
          <span class="tnum text-body text-label-tertiary">
            {{ bugStore.openTotal }} 条未解决
          </span>
        </template>
        <template #filters>
          <Input v-model="bugStore.keyword" placeholder="搜索" size="lg" class="w-full sm:w-52" />
          <SegmentedControl v-model="bugStore.scope" :items="scopeOptions" size="lg" />
        </template>
        <template #actions>
          <Button variant="primary" size="lg" @click="createOpen = true">
            <Icon :as="Plus" size="inline" />录入 Bug
          </Button>
        </template>
      </ListToolbar>

      <div class="scroll-area min-h-0 flex-1 overflow-auto">
        <p v-if="bugStore.error" class="border-b border-separator px-5 py-2 text-caption text-danger">
          {{ bugStore.error }}
        </p>
        <Table
          :columns="columns"
          :rows="bugStore.bugs"
          :selected-id="selectedId"
          :show-empty="false"
          @select="selectedId = $event.id as number"
        >
          <template #cell="{ row, column }">
            <BugSeverityBadge
              v-if="column.key === 'severity'"
              :severity="row.severity as BugSeverity"
            />
            <BugStatusBadge v-else-if="column.key === 'status'" :status="row.status as BugStatus" />
            <span
              v-else-if="column.key === 'source'"
              class="tnum text-label-tertiary group-data-[selected=true]:text-white"
            >
              {{ taskLabel(row.sourceTaskId) }}
            </span>
            <span
              v-else-if="column.key === 'foundAt'"
              class="tnum text-label-tertiary group-data-[selected=true]:text-white"
            >
              {{ formatRelative(row.foundAt) }}
            </span>
          </template>
        </Table>

        <EmptyState
          v-if="bugStore.total === 0"
          title="没有匹配的缺陷"
          :description="
            bugStore.scope === 'OPEN'
              ? '人工录入或执行过程中报出的问题会进入这里'
              : '处理过的缺陷会留在这'
          "
        />
      </div>

      <Pagination
        v-model:page="bugStore.page"
        v-model:page-size="bugStore.pageSize"
        :total="bugStore.total"
      />
    </div>

    <!-- 与任务页同一套：桌面右栏，手机整屏覆盖层（390px 放不下并排两栏） -->
    <aside
      :class="[
        'bg-window',
        selected
          ? 'fixed inset-0 z-40 w-full overflow-y-auto border-separator p-4 lg:static lg:inset-auto lg:z-auto lg:w-72 lg:shrink-0 lg:border-l'
          : 'hidden lg:block lg:w-72 lg:shrink-0 lg:overflow-y-auto lg:border-l lg:border-separator lg:p-4',
      ]"
    >
      <template v-if="selected">
        <button
          type="button"
          class="mb-3 inline-flex items-center gap-0.5 text-caption text-accent lg:hidden"
          @click="selectedId = null"
        >
          <Icon :as="ChevronLeft" size="inline" />返回列表
        </button>
        <div class="flex items-center gap-2">
          <span class="tnum text-caption text-label-tertiary">#{{ selected.id }}</span>
          <BugSeverityBadge :severity="selected.severity" />
          <BugStatusBadge :status="selected.status" class="ml-auto" />
        </div>
        <h2 class="mt-2 text-body font-emphasis text-label">{{ selected.title }}</h2>
        <p class="mt-1 text-caption text-label-secondary">{{ context[selected.status] }}</p>

        <div class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5">
          <p class="text-caption font-emphasis text-label-secondary">复现步骤</p>
          <p class="mt-1 whitespace-pre-line text-caption text-label">
            {{ selected.reproduction || '未填写' }}
          </p>
        </div>

        <div class="mt-4 border-t border-separator pt-3">
          <div class="flex items-center gap-2">
            <p class="text-caption font-emphasis text-label-secondary">图片</p>
            <span v-if="attachments.length > 0" class="tnum text-caption text-label-tertiary">
              {{ attachments.length }}
            </span>
            <Tooltip content="加一张图">
              <IconButton
                label="添加图片"
                size="sm"
                class="ml-auto"
                :disabled="attaching"
                @click="attachInput?.click()"
              >
                <Icon :as="ImagePlus" size="regular" />
              </IconButton>
            </Tooltip>
          </div>

          <ImageAttachmentGrid
            v-if="attachments.length > 0"
            :attachments="attachments"
            class="mt-2"
            @select="openViewer"
          />
          <p v-else class="mt-2 text-caption text-label-tertiary">
            还没有图片。报错现场、界面错位这类问题可以直接截图贴上来。
          </p>
          <p v-if="attachmentError" class="mt-2 text-caption text-danger">
            {{ attachmentError }}
          </p>

          <input
            ref="attachInput"
            type="file"
            :accept="ACCEPTED_IMAGE_TYPES.join(',')"
            multiple
            class="hidden"
            @change="attachFiles"
          />
        </div>

        <div
          v-if="selected.filePath || selected.detail"
          class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5"
        >
          <p class="text-caption font-emphasis text-label-secondary">定位与说明</p>
          <p v-if="selected.filePath" class="mt-1 break-all font-mono text-caption text-label">
            {{ selected.filePath }}
          </p>
          <p v-if="selected.detail" class="mt-1 whitespace-pre-line text-caption text-label-secondary">
            {{ selected.detail }}
          </p>
        </div>

        <div
          v-if="selected.wontFixReason"
          class="mt-4 rounded-panel border border-separator-soft bg-content p-2.5"
        >
          <p class="text-caption font-emphasis text-label-secondary">不修复的理由</p>
          <p class="mt-1 text-caption text-label">{{ selected.wontFixReason }}</p>
        </div>

        <!--
          任务标题在 288px 里放不进「标签在左、值在右」的一行，所以长值改成上下两行，
          短的（时间）仍走一行两列 —— 这是 macOS 检查器的一贯做法，不是随手换的版式。
        -->
        <dl class="mt-4 space-y-3">
          <div>
            <dt class="text-caption text-label-secondary">项目</dt>
            <dd class="mt-0.5 text-caption text-label">{{ selected.projectName }}</dd>
          </div>
          <div>
            <dt class="text-caption text-label-secondary">来源</dt>
            <dd class="mt-0.5 text-caption text-label">{{ taskLabel(selected.sourceTaskId) }}</dd>
          </div>
          <div>
            <dt class="text-caption text-label-secondary">修复任务</dt>
            <dd class="mt-0.5 text-caption text-label">
              {{ selected.fixTaskId ? taskLabel(selected.fixTaskId) : '尚未派发' }}
            </dd>
          </div>
          <div class="flex items-center gap-2">
            <dt class="text-caption text-label-secondary">发现时间</dt>
            <dd class="tnum ml-auto text-caption text-label">
              {{ formatRelative(selected.foundAt) }}
            </dd>
          </div>
        </dl>

        <div class="mt-4 flex flex-wrap items-center gap-2 border-t border-separator pt-3">
          <Button
            v-if="canRequestFix(selected.status)"
            variant="primary"
            size="sm"
            :disabled="requestingFix"
            @click="requestFix"
          >
            <Icon :as="Wrench" size="inline" />派发修复任务
          </Button>
          <Button
            v-if="canMarkFixed(selected.status)"
            size="sm"
            :disabled="changingStatus"
            @click="markFixed"
          >
            标记已修复
          </Button>
          <Button
            v-if="canVerify(selected.status)"
            variant="primary"
            size="sm"
            :disabled="changingStatus"
            @click="verify"
          >
            <Icon :as="ShieldCheck" size="inline" />验证关闭
          </Button>
          <Button
            v-if="canWontFix(selected.status)"
            size="sm"
            :disabled="changingStatus"
            @click="openWontFix"
          >
            不修复
          </Button>
        </div>
      </template>

      <p v-else class="text-caption text-label-tertiary">选中一行查看详情</p>
    </aside>

    <Dialog
      v-model:open="wontFixOpen"
      layout="form"
      title="标记为不修复？"
      description="缺陷单不会消失，理由会留在上面。将来的你（或别人）只能靠这句话判断当初为什么放过了它。"
    >
      <div class="flex items-center gap-2">
        <span class="tnum text-caption text-label-tertiary">#{{ selected?.id }}</span>
        <BugSeverityBadge v-if="selected" :severity="selected.severity" />
      </div>
      <p class="mt-1.5 text-body text-label">{{ selected?.title }}</p>

      <Textarea
        v-model="wontFixReason"
        class="mt-3"
        :rows="3"
        placeholder="例如：这是上游接口的历史遗留行为，改了会连带影响三个月前的对账数据，等下个版本统一处理"
      />
      <p v-if="wontFixReason.length > 0 && !canWontFixConfirm" class="mt-1.5 text-caption text-danger">
        至少 {{ WONTFIX_MIN }} 个字，否则这句话等于没写
      </p>

      <template #footer>
        <Button @click="wontFixOpen = false">取消</Button>
        <Button :disabled="!canWontFixConfirm || changingStatus" @click="confirmWontFix">
          {{ changingStatus ? '保存中…' : '确认不修复' }}
        </Button>
      </template>
    </Dialog>

    <BugCreateDialog
      v-model:open="createOpen"
      @created="selectedId = $event.id"
      @uploaded="loadAttachments($event.id)"
    />

    <ImageAttachmentsDialog
      v-model:open="viewerOpen"
      v-model:selected-id="viewerId"
      :attachments="attachments"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { createProject, updateProject } from '@/api/projects'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Input from '@/components/ui/input/Input.vue'
import Switch from '@/components/ui/switch/Switch.vue'

import { useToast } from '@/composables/useToast'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useOrganizationStore } from '@/stores/useOrganizationStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Project } from '@/types/api'

/*
 * 注册项目。
 *
 * 它原来长在「新建任务」弹框里（没有项目时就地摊开一张表单）。把它搬出来是因为：
 * 项目比任务长命得多 —— 注册一次，之后一直用它，而「新建任务」是一条一次性的路径。
 * 让建项目必须走「先想好一个任务 → 发现没有项目 → 顺手建一个」，等于把两件事绑在一起。
 * 现在入口在工具栏（项目下拉旁边）、项目页，以及新建任务弹框里「一个项目都没有」时的提示按钮。
 *
 * 表单排法是 macOS 对话框的样子：标签右对齐在左列、控件在右列、说明落在控件下方。
 * 见 DialogField 的注释。
 */
const emit = defineEmits<{
  registered: [project: Project]
  updated: [project: Project]
}>()

const open = defineModel<boolean>('open', { default: false })

const toast = useToast()
const router = useRouter()
const store = useTaskStore()
const organizationStore = useOrganizationStore()
// 同一个弹框只挂在 App 上，页面想「知道刚建了什么」得从这个共享位子上读
const { editingProject, lastRegistered, lastUpdated } = useProjectDialog()

const name = ref('')
const enabled = ref(true)
const error = ref<string | null>(null)
const saving = ref(false)
const editing = computed(() => editingProject.value)

const canSave = computed(
  () =>
    (editing.value || organizationStore.currentId !== null)
    && (editing.value || organizationStore.canManageProjects)
    && name.value.trim().length > 0,
)

// 注册时清空；编辑时填入当前值。两种模式共用同一个表单，避免字段与校验漂移。
watch(open, (value) => {
  if (!value) return
  const project = editingProject.value
  name.value = project?.name ?? ''
  enabled.value = project?.enabled ?? true
  error.value = null
})

const save = async () => {
  if (!canSave.value || saving.value) return
  saving.value = true
  error.value = null
  try {
    const current = editingProject.value
    const saved = current
      ? await updateProject(current.id, {
          name: name.value.trim(),
          path: current.path,
          defaultDecisionLevel: current.defaultDecisionLevel,
          versionSource: current.versionSource,
          fallbackVersion: current.fallbackVersion,
          enabled: enabled.value,
        })
      : await createProject({
          organizationId: organizationStore.currentId!,
          code: '',
          name: name.value.trim(),
          path: null,
          defaultDecisionLevel: 'L1',
          versionSource: 'GIT_TAG',
          fallbackVersion: null,
        })
    // 重新拉而不是本地 push：库里回填了什么（ID、启用状态）由后端说了算，
    // 而且工具栏的项目下拉要立刻看到最新名称与状态
    await store.loadProjects()
    open.value = false
    if (editingProject.value) {
      lastUpdated.value = saved
      emit('updated', saved)
      toast.success(
        `项目「${saved.name}」已更新`,
        saved.enabled ? '项目恢复派发新任务' : '项目已暂停派发，历史任务与资料保持不变',
      )
    } else {
      lastRegistered.value = saved
      emit('registered', saved)
      store.projectId = saved.id
      await router.replace({ path: '/projects', query: { view: 'members' } })
      toast.success(
        `项目「${saved.name}」已创建`,
        '第一步请配置项目团队与角色，所有版本共用这套角色',
      )
    }
  } catch (e) {
    // 失败原因留在表单里而不是弹 toast：人正对着这几行输入，改的就是它们
    error.value = e instanceof ApiError ? e.message : '无法连接后端，请确认服务已启动（默认 127.0.0.1:8787）'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    :title="editing ? '编辑项目' : '新建项目'"
    :description="editing
      ? '维护项目名称和派发状态。'
      : '创建项目后先配置团队角色，再进入版本规划和版本立项。'"
  >
    <div class="flex flex-col gap-3">
      <DialogField
        v-if="!editing"
        label="所属组织"
        :hint="organizationStore.current ? '项目创建后不能更换组织' : '请先创建或切换到一个组织'"
      >
        <div class="rounded-control border border-separator-soft bg-window px-3 py-2 text-caption text-label">
          {{ organizationStore.current?.name ?? '尚未选择组织' }}
        </div>
      </DialogField>

      <DialogField label="名称">
        <Input v-model="name" :maxlength="128" placeholder="例如：需求执行平台" />
      </DialogField>

      <div
        v-if="!editing"
        class="rounded-panel border border-accent/20 bg-accent/5 px-3 py-2.5"
      >
        <p class="text-caption font-emphasis text-label">创建后第一步：配置团队角色</p>
        <p class="mt-1 text-caption text-label-tertiary">
          团队成员角色属于整个项目/产品，所有版本和研发流程共用。代码目录由开发成员后续绑定。
        </p>
      </div>

      <DialogField
        v-if="editing"
        label="派发状态"
        hint="关闭后不再派发新任务，历史任务与项目摘要保持不变"
      >
        <Switch v-model="enabled" :label="enabled ? '启用，允许派发' : '已暂停，不派发新任务'" />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
      <p v-if="!editing && !organizationStore.currentId" class="text-caption text-warning">
        当前账号还没有组织。请先到“组织管理”创建组织，再新建项目。
      </p>
      <p
        v-else-if="!editing && !organizationStore.canManageProjects"
        class="text-caption text-warning"
      >
        当前组织角色不能创建项目，请让组织管理员分配“项目经理”或更高角色。
      </p>
    </div>

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave || saving" @click="save">
        {{ saving ? '保存中…' : editing ? '保存修改' : '创建项目' }}
      </Button>
    </template>
  </Dialog>
</template>

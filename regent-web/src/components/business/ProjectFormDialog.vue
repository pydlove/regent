<script setup lang="ts">
import { FolderOpen } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/http'
import { createProject, listDirectories } from '@/api/projects'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import DialogField from '@/components/ui/dialog/DialogField.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Select from '@/components/ui/select/Select.vue'

import DirectoryPickerDialog from './DirectoryPickerDialog.vue'
import { useToast } from '@/composables/useToast'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import { useTaskStore } from '@/stores/useTaskStore'
import type { Project } from '@/types/api'
import type { DecisionLevel } from '@/types/task'

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
const emit = defineEmits<{ registered: [project: Project] }>()

const open = defineModel<boolean>('open', { default: false })

const toast = useToast()
const store = useTaskStore()
const executionRuntime = useExecutionRuntimeStore()
// 同一个弹框只挂在 App 上，页面想「知道刚建了什么」得从这个共享位子上读
const { lastRegistered } = useProjectDialog()

const code = ref('')
const name = ref('')
const path = ref('')
const level = ref<string>('L1')
const error = ref<string | null>(null)
const saving = ref(false)

const levelOptions = [
  { value: 'L0', label: 'L0 · 完全自主' },
  { value: 'L1', label: 'L1 · 自主但进日报' },
  { value: 'L2', label: 'L2 · 需人工批准' },
  { value: 'L3', label: 'L3 · 强阻塞' },
]

const codeValid = computed(() => /^[a-z][a-z0-9_]{0,63}$/.test(code.value))

/*
 * 路径可以手打（从别处复制一整条更省事），也可以点「选择…」从本机目录树里挑。
 *
 * 失焦时问一次后端「这个目录在不在」：判据只能在那一侧（也只有它问得到本机文件系统），
 * 前端负责把结论摆在输入框下面。等提交才报错的话，人多半已经在想别的事了，
 * 回来只看到一句红字，还得自己回想是哪一行。
 */
const pathError = ref<string | null>(null)
const pickerOpen = ref(false)

const checkPath = async () => {
  pathError.value = null
  const value = path.value.trim()
  // 连绝对路径都不是，就不用问后端了 —— 提交时它还会再拦一次
  if (!value.startsWith('/')) return
  try {
    await listDirectories(value)
  } catch (e) {
    pathError.value = e instanceof ApiError ? e.message : '读不到这个目录'
  }
}

const onPicked = (picked: string) => {
  path.value = picked
  pathError.value = null
}
const canSave = computed(
  () => codeValid.value && name.value.trim().length > 0 && path.value.trim().startsWith('/'),
)

// 每次打开都清空：上一次填了一半的内容留着，容易被当成占位文字直接提交
watch(open, (value) => {
  if (!value) return
  code.value = ''
  name.value = ''
  path.value = ''
  level.value = 'L1'
  error.value = null
  pathError.value = null
  pickerOpen.value = false
})

const save = async () => {
  if (!canSave.value || saving.value) return
  saving.value = true
  error.value = null
  try {
    const created = await createProject({
      code: code.value.trim(),
      name: name.value.trim(),
      path: path.value.trim(),
      defaultDecisionLevel: level.value as DecisionLevel,
    })
    // 重新拉而不是本地 push：库里回填了什么（ID、启用状态）由后端说了算，
    // 而且工具栏的项目下拉要立刻看到这个新项目
    await store.loadProjects()
    lastRegistered.value = created
    open.value = false
    emit('registered', created)
    if (executionRuntime.status?.available === false) {
      toast.warning(
        `项目「${created.name}」已注册`,
        'Codex 尚未就绪，项目资料暂未生成；安装后可在项目页重新生成',
      )
    } else {
      toast.success(`项目「${created.name}」已注册`, 'Codex 正在只读分析项目并生成项目资料')
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
    title="注册项目"
    description="项目决定 codex 在哪个目录跑，以及多高的决策必须惊动人。"
  >
    <div class="flex flex-col gap-3">
      <DialogField label="标识" hint="小写字母开头，字母数字下划线，建后不改">
        <Input v-model="code" placeholder="例如：regent" :invalid="code.length > 0 && !codeValid" />
      </DialogField>

      <DialogField label="名称">
        <Input v-model="name" placeholder="例如：需求执行平台" />
      </DialogField>

      <DialogField label="路径" hint="codex 的工作目录，必须在本机已存在">
        <div class="flex items-center gap-2">
          <!-- 包一层给 flex-1：Input 的变体里有 w-full，直接挂 flex-1 谁生效取决于
               Tailwind 产物顺序（这个坑在优先级输入框上踩过一次） -->
          <div class="min-w-0 flex-1">
            <Input
              v-model="path"
              placeholder="/Users/you/workspace/my-project"
              :invalid="pathError !== null"
              @blur="checkPath"
            />
          </div>
          <Button @click="pickerOpen = true">
            <Icon :as="FolderOpen" size="inline" />选择…
          </Button>
        </div>
        <p v-if="pathError" class="mt-1 text-caption text-danger">{{ pathError }}</p>
      </DialogField>

      <DialogField label="决策阈值" hint="高于这个等级的决策一律升级人工">
        <Select v-model="level" :items="levelOptions" block />
      </DialogField>

      <p v-if="error" class="text-caption text-danger">{{ error }}</p>
    </div>

    <!-- 选择器挂在注册弹框里：它只在填路径时需要，开在别处就要多一份「刚才在填哪个项目」的状态 -->
    <DirectoryPickerDialog v-model:open="pickerOpen" :initial="path" @picked="onPicked" />

    <template #footer>
      <Button @click="open = false">取消</Button>
      <Button variant="primary" :disabled="!canSave || saving" @click="save">
        {{ saving ? '注册中…' : '注册项目' }}
      </Button>
    </template>
  </Dialog>
</template>

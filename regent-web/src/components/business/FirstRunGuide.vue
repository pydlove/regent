<script setup lang="ts">
import { ArrowRight, Check, Circle, X } from '@lucide/vue'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import Button from '@/components/ui/button/Button.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import { useProjectDialog } from '@/composables/useProjectDialog'
import { useAuthStore } from '@/stores/useAuthStore'
import { useDecisionStore } from '@/stores/useDecisionStore'
import { useExecutionRuntimeStore } from '@/stores/useExecutionRuntimeStore'
import { useTaskStore } from '@/stores/useTaskStore'

const auth = useAuthStore()
const taskStore = useTaskStore()
const decisionStore = useDecisionStore()
const runtime = useExecutionRuntimeStore()
const router = useRouter()
const { openDialog: openProjectDialog } = useProjectDialog()
const dismissed = ref(false)

const storageKey = computed(() =>
  auth.user?.id ? `regent.first-run.dismissed.${auth.user.id}` : '',
)
try {
  dismissed.value = Boolean(storageKey.value && localStorage.getItem(storageKey.value) === '1')
} catch {
  dismissed.value = false
}

const deviceReady = computed(
  () => runtime.device?.paired === true && runtime.status?.available === true,
)
const steps = computed(() => [
  {
    key: 'project',
    label: '新建项目',
    description: '创建研发项目并从立项开始',
    done: taskStore.projects.length > 0,
    action: '新建项目',
  },
  {
    key: 'device',
    label: '连接执行设备',
    description: '确认设备代理与 Codex 可用',
    done: deviceReady.value,
    action: '检查设备',
  },
  {
    key: 'task',
    label: '创建第一个任务',
    description: '写下目标、约束和验收标准',
    done: taskStore.taskTotal > 0,
    action: '创建任务',
  },
])
const currentStep = computed(() => steps.value.find((step) => !step.done) ?? null)
const visible = computed(
  () => !dismissed.value && Boolean(auth.user?.id) && Boolean(currentStep.value),
)

const dismiss = () => {
  dismissed.value = true
  try {
    if (storageKey.value) localStorage.setItem(storageKey.value, '1')
  } catch {
    // 用户禁用本地存储时仅隐藏本次页面，不影响主流程。
  }
}

const runCurrentStep = () => {
  if (currentStep.value?.key === 'project') {
    openProjectDialog()
    return
  }
  if (currentStep.value?.key === 'device') {
    void router.push('/settings/execution')
    return
  }
  void router.push('/tasks')
}
</script>

<template>
  <section
    v-if="visible"
    class="mt-3 flex flex-wrap items-center gap-x-5 gap-y-3 border-y border-accent/20 bg-accent/5 px-4 py-3"
    aria-label="首次使用引导"
  >
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
        <span
          v-for="(step, index) in steps"
          :key="step.key"
          class="flex items-center gap-1.5 text-caption"
          :class="step.done ? 'text-success' : step.key === currentStep?.key ? 'font-emphasis text-label' : 'text-label-tertiary'"
        >
          <Icon :as="step.done ? Check : Circle" size="inline" />
          <span class="tnum text-label-tertiary">{{ index + 1 }}</span>
          <span>{{ step.label }}</span>
        </span>
      </div>
      <p class="mt-1 text-caption text-label-secondary">
        下一步：{{ currentStep?.label }}，{{ currentStep?.description }}。
        已有 {{ taskStore.projects.length }} 个项目、{{ decisionStore.pendingTotal }} 项待决策。
      </p>
    </div>
    <div class="flex items-center gap-2">
      <Button size="sm" @click="dismiss">
        <Icon :as="X" size="inline" />跳过
      </Button>
      <Button variant="primary" size="sm" @click="runCurrentStep">
        {{ currentStep?.action }}
        <Icon :as="ArrowRight" size="inline" />
      </Button>
    </div>
  </section>
</template>

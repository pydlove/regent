<script setup lang="ts">
import { Plus, Trash2 } from '@lucide/vue'

import Button from '@/components/ui/button/Button.vue'
import Checkbox from '@/components/ui/checkbox/Checkbox.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import Textarea from '@/components/ui/input/Textarea.vue'
import type { RequirementTaskDraft } from '@/types/api'

const props = defineProps<{
  tasks: RequirementTaskDraft[]
}>()
const emit = defineEmits<{ 'update:tasks': [tasks: RequirementTaskDraft[]] }>()

const update = (index: number, patch: Partial<RequirementTaskDraft>) => {
  emit('update:tasks', props.tasks.map((task, current) =>
    current === index ? { ...task, ...patch } : task,
  ))
}

const updateList = (index: number, field: 'scope' | 'outOfScope', value: string) => {
  update(index, {
    [field]: value.split(/[,，\n]+/).map((item) => item.trim()).filter(Boolean),
  })
}

const updateAcceptance = (index: number, value: string) => {
  update(index, {
    acceptanceCriteria: value.split('\n').map((item) => item.trim()).filter(Boolean),
  })
}

const toggleDependency = (index: number, dependency: string) => {
  const task = props.tasks[index]
  if (!task || task.key === dependency) return
  const dependencies = task.dependencies.includes(dependency)
    ? task.dependencies.filter((item) => item !== dependency)
    : [...task.dependencies, dependency]
  update(index, { dependencies })
}

const remove = (index: number) => {
  emit('update:tasks', props.tasks.filter((_, current) => current !== index))
}

const add = () => {
  if (props.tasks.length >= 12) return
  const next = props.tasks.length + 1
  emit('update:tasks', [
    ...props.tasks,
    {
      key: `T${next}`,
      title: '',
      objective: '',
      scope: [],
      outOfScope: [],
      acceptanceCriteria: [],
      dependencies: [],
      priority: 0,
      risk: 'L1',
      suggestedOrder: next,
    },
  ])
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <article
      v-for="(task, index) in tasks"
      :key="`${task.key}-${index}`"
      class="rounded-panel border border-separator-soft bg-content p-3"
    >
      <div class="flex items-center gap-2">
        <span class="rounded-badge bg-accent/10 px-1.5 py-0.5 font-mono text-caption text-accent">
          {{ task.key }}
        </span>
        <Input
          :model-value="task.title"
          class="min-w-0 flex-1"
          placeholder="任务标题"
          @update:model-value="update(index, { title: $event })"
        />
        <Button size="sm" variant="ghost" :disabled="tasks.length <= 1" @click="remove(index)">
          <Icon :as="Trash2" size="inline" />
        </Button>
      </div>

      <label class="mt-2 block">
        <span class="mb-1 block text-caption text-label-secondary">任务目标</span>
        <Textarea
          :model-value="task.objective"
          :rows="2"
          placeholder="完成后应达到什么结果"
          @update:model-value="update(index, { objective: $event })"
        />
      </label>

      <div class="mt-2 grid gap-2 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-caption text-label-secondary">工作范围</span>
          <Textarea
            :model-value="task.scope.join('，')"
            :rows="2"
            placeholder="多个范围用逗号或换行分隔"
            @update:model-value="updateList(index, 'scope', $event)"
          />
        </label>
        <label class="block">
          <span class="mb-1 block text-caption text-label-secondary">非目标</span>
          <Textarea
            :model-value="task.outOfScope.join('，')"
            :rows="2"
            placeholder="明确本次不处理的内容"
            @update:model-value="updateList(index, 'outOfScope', $event)"
          />
        </label>
      </div>

      <label class="mt-2 block">
        <span class="mb-1 block text-caption text-label-secondary">验收标准，每行一条</span>
        <Textarea
          :model-value="task.acceptanceCriteria.join('\n')"
          :rows="3"
          placeholder="可验证的结果、测试或操作路径"
          @update:model-value="updateAcceptance(index, $event)"
        />
      </label>

      <div class="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 border-t border-separator-soft pt-2">
        <span class="text-caption text-label-secondary">依赖任务</span>
        <Checkbox
          v-for="candidate in tasks.filter((item) => item.key !== task.key)"
          :key="candidate.key"
          :model-value="task.dependencies.includes(candidate.key)"
          :label="candidate.key"
          @update:model-value="toggleDependency(index, candidate.key)"
        />
        <span class="text-caption text-label-tertiary">无依赖时可省略</span>
      </div>
    </article>

    <Button size="sm" :disabled="tasks.length >= 12" class="self-start" @click="add">
      <Icon :as="Plus" size="inline" />添加任务
    </Button>
  </div>
</template>

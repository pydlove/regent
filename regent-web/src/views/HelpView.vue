<script setup lang="ts">
import {
  ArrowRight,
  BookOpen,
  ChevronDown,
  CircleHelp,
  FolderKanban,
  History,
  Lightbulb,
  ListChecks,
  LockKeyhole,
  Play,
  Search,
  SearchX,
  Workflow,
  X,
} from '@lucide/vue'
import { computed, nextTick, ref, watch, type Component } from 'vue'
import { RouterLink } from 'vue-router'

import Button from '@/components/ui/button/Button.vue'
import { buttonVariants } from '@/components/ui/button/button.variants'
import EmptyState from '@/components/ui/empty-state/EmptyState.vue'
import Icon from '@/components/ui/icon/Icon.vue'
import Input from '@/components/ui/input/Input.vue'
import {
  HELP_CATEGORIES,
  HELP_TOPICS,
  QUICK_START_STEPS,
  filterHelpTopics,
  helpCategoryLabel,
  type HelpCategoryId,
  type HelpFilter,
} from '@/content/help'

const categoryIcons: Record<HelpCategoryId, Component> = {
  start: BookOpen,
  tasks: ListChecks,
  decisions: Workflow,
  configuration: FolderKanban,
  records: History,
  account: LockKeyhole,
}

const categoryTones: Record<HelpCategoryId, string> = {
  start: 'bg-accent/12 text-accent',
  tasks: 'bg-accent/12 text-accent',
  decisions: 'bg-warning/15 text-warning',
  configuration: 'bg-success/12 text-success',
  records: 'bg-danger/10 text-danger',
  account: 'bg-neutral/15 text-neutral',
}

const quickStartIcons: Component[] = [FolderKanban, ListChecks, Play, Workflow, History]

const query = ref('')
const activeCategory = ref<HelpFilter>('all')
const expandedIds = ref(new Set<string>([HELP_TOPICS[0].id]))
const faqSection = ref<HTMLElement | null>(null)

const filteredTopics = computed(() =>
  filterHelpTopics(HELP_TOPICS, query.value, activeCategory.value),
)

const categoryCounts = computed<Record<HelpFilter, number>>(() => {
  const counts = {
    all: filterHelpTopics(HELP_TOPICS, query.value).length,
  } as Record<HelpFilter, number>
  for (const category of HELP_CATEGORIES) {
    counts[category.id] = filterHelpTopics(HELP_TOPICS, query.value, category.id).length
  }
  return counts
})

const categoryItems = computed(() => [
  {
    id: 'all' as const,
    label: '全部问题',
    description: '查看全部 FAQ',
    icon: CircleHelp,
    tone: categoryTones.start,
    count: categoryCounts.value.all,
  },
  ...HELP_CATEGORIES.map((category) => ({
    ...category,
    icon: categoryIcons[category.id],
    tone: categoryTones[category.id],
    count: categoryCounts.value[category.id],
  })),
])

const selectedCategory = computed(
  () =>
    categoryItems.value.find((category) => category.id === activeCategory.value) ??
    categoryItems.value[0],
)

const resultDescription = computed(() => {
  if (query.value.trim()) {
    return `${selectedCategory.value.label}中找到 ${filteredTopics.value.length} 条匹配说明`
  }
  return selectedCategory.value.description
})

const resultAnnouncement = computed(
  () => `${selectedCategory.value.label}，当前显示 ${filteredTopics.value.length} 条常见问题`,
)

function selectCategory(category: HelpFilter): void {
  activeCategory.value = category
  void scrollToFaq()
}

async function scrollToFaq(): Promise<void> {
  await nextTick()
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  faqSection.value?.scrollIntoView({
    behavior: reducedMotion ? 'auto' : 'smooth',
    block: 'start',
  })
}

function toggleTopic(id: string): void {
  const next = new Set(expandedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  expandedIds.value = next
}

function clearSearch(): void {
  query.value = ''
}

function resetFilters(): void {
  query.value = ''
  activeCategory.value = 'all'
  void scrollToFaq()
}

watch([query, activeCategory], () => {
  const first = filteredTopics.value[0]
  expandedIds.value = first ? new Set([first.id]) : new Set()
})
</script>

<template>
  <div class="flex h-full min-h-0 bg-window max-lg:flex-col">
    <aside
      class="flex w-[236px] shrink-0 flex-col bg-sidebar/70 px-3 py-4 max-lg:w-full max-lg:border-b max-lg:border-separator max-lg:px-2 max-lg:py-2"
      aria-label="使用说明导航"
    >
      <div class="relative" role="search">
        <Icon
          :as="Search"
          size="inline"
          class="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-label-tertiary"
        />
        <Input
          v-model="query"
          size="lg"
          type="text"
          inputmode="search"
          class="pl-8 pr-8"
          placeholder="搜索使用说明"
          aria-label="搜索常见问题"
          @keydown.enter="scrollToFaq"
          @keydown.esc="clearSearch"
        />
        <button
          v-if="query"
          type="button"
          class="absolute right-1 top-1/2 grid size-5 -translate-y-1/2 place-items-center rounded-full text-label-tertiary transition-colors duration-[120ms] ease-apple hover:bg-hover hover:text-label focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-accent/35"
          aria-label="清除搜索"
          @click="clearSearch"
        >
          <Icon :as="X" size="inline" />
        </button>
      </div>

      <nav class="mt-5 flex min-h-0 flex-1 flex-col max-lg:mt-2" aria-label="FAQ 分类">
        <p class="px-2 pb-1 text-caption font-emphasis text-label-tertiary max-lg:hidden">
          问题分类
        </p>
        <div class="scroll-area flex flex-col gap-1 max-lg:flex-row max-lg:overflow-x-auto max-lg:pb-1">
          <button
            v-for="category in categoryItems"
            :key="category.id"
            type="button"
            :aria-current="activeCategory === category.id ? 'page' : undefined"
            :class="[
              'group flex h-row shrink-0 items-center gap-2 rounded-control px-2 text-left text-body transition-colors duration-[120ms] ease-apple focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35 lg:w-full',
              activeCategory === category.id
                ? 'bg-accent text-white shadow-control'
                : 'text-label hover:bg-hover',
            ]"
            @click="selectCategory(category.id)"
          >
            <span
              :class="[
                'grid size-5 shrink-0 place-items-center rounded-[5px]',
                activeCategory === category.id ? 'bg-white/20 text-white' : category.tone,
              ]"
            >
              <Icon :as="category.icon" size="inline" />
            </span>
            <span class="truncate">{{ category.label }}</span>
            <span
              :class="[
                'tnum ml-auto pl-2 text-caption',
                activeCategory === category.id ? 'text-white/75' : 'text-label-tertiary',
              ]"
            >
              {{ category.count }}
            </span>
          </button>
        </div>

        <div class="mt-auto hidden px-2 pt-5 lg:block">
          <div class="flex items-center gap-2 text-caption text-label-tertiary">
            <Icon :as="BookOpen" size="inline" />
            <span>共 {{ HELP_TOPICS.length }} 条说明</span>
          </div>
          <p class="mt-1 text-caption text-label-tertiary">按使用阶段整理，可随时搜索</p>
        </div>
      </nav>
    </aside>

    <section class="scroll-area min-h-0 min-w-0 flex-1 overflow-y-auto">
      <div class="mx-auto w-full max-w-[980px] px-5 py-6 sm:px-7 lg:px-9 lg:py-8">
        <header class="flex items-start gap-3.5">
          <span
            class="grid size-10 shrink-0 place-items-center rounded-panel bg-accent/12 text-accent"
          >
            <Icon :as="BookOpen" size="large" />
          </span>
          <div class="min-w-0">
            <p class="text-caption font-emphasis text-accent">帮助中心</p>
            <h1 class="mt-0.5 text-metric font-emphasis text-label">第一次使用 Regent</h1>
            <p class="mt-1 max-w-2xl text-body text-label-secondary">
              从注册项目到核对执行结果，先按主流程完成任务，再用 FAQ 处理具体问题。
            </p>
          </div>
        </header>

        <section class="mt-7" aria-labelledby="quick-start-title">
          <div class="flex items-end justify-between gap-4">
            <div>
              <h2 id="quick-start-title" class="text-title font-emphasis text-label">
                五步走通一条任务
              </h2>
              <p class="mt-0.5 text-caption text-label-secondary">
                每一步都能直接进入对应页面，不需要记住入口。
              </p>
            </div>
            <span class="tnum shrink-0 text-caption text-label-tertiary">5 个步骤</span>
          </div>

          <ol class="mt-3 overflow-hidden rounded-panel bg-content shadow-panel">
            <li v-for="(step, index) in QUICK_START_STEPS" :key="step.title">
              <RouterLink
                :to="step.to"
                class="group flex min-h-[68px] items-center gap-3 px-4 py-3 transition-colors duration-[120ms] ease-apple hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-accent/35"
                :class="index > 0 ? 'border-t border-separator-soft' : ''"
              >
                <span
                  class="tnum grid size-7 shrink-0 place-items-center rounded-full bg-selection text-caption font-emphasis text-accent"
                >
                  {{ index + 1 }}
                </span>
                <span
                  class="grid size-7 shrink-0 place-items-center rounded-[7px] bg-hover text-label-secondary max-sm:hidden"
                >
                  <Icon :as="quickStartIcons[index]" size="inline" />
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block text-body font-emphasis text-label">{{ step.title }}</span>
                  <span class="mt-0.5 block text-caption text-label-secondary">
                    {{ step.description }}
                  </span>
                </span>
                <span
                  class="hidden shrink-0 items-center gap-1 text-caption text-accent sm:flex"
                >
                  {{ step.action }}
                  <Icon :as="ArrowRight" size="inline" />
                </span>
                <Icon
                  :as="ArrowRight"
                  size="inline"
                  class="shrink-0 text-label-tertiary sm:hidden"
                />
              </RouterLink>
            </li>
          </ol>
        </section>

        <section
          ref="faqSection"
          class="mt-8 scroll-mt-6"
          aria-labelledby="faq-title"
        >
          <div class="flex flex-wrap items-end gap-x-4 gap-y-1">
            <div class="min-w-0">
              <h2 id="faq-title" class="text-title font-emphasis text-label">常见问题</h2>
              <p class="mt-0.5 text-caption text-label-secondary">{{ resultDescription }}</p>
            </div>
            <span
              class="tnum ml-auto shrink-0 rounded-full bg-selection px-2.5 py-1 text-caption font-emphasis text-accent"
            >
              {{ filteredTopics.length }} 条
            </span>
          </div>

          <p class="sr-only" role="status" aria-live="polite">{{ resultAnnouncement }}</p>

          <div
            v-if="filteredTopics.length > 0"
            class="mt-3 overflow-hidden rounded-panel bg-content shadow-panel"
          >
            <article
              v-for="(topic, index) in filteredTopics"
              :key="topic.id"
              :class="index > 0 ? 'border-t border-separator-soft' : ''"
            >
              <h3>
                <button
                  :id="`help-question-${topic.id}`"
                  type="button"
                  class="group flex w-full items-start gap-3 px-4 py-3 text-left transition-colors duration-[120ms] ease-apple hover:bg-hover focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-inset focus-visible:ring-accent/35"
                  :aria-expanded="expandedIds.has(topic.id)"
                  :aria-controls="`help-answer-${topic.id}`"
                  @click="toggleTopic(topic.id)"
                >
                  <span
                    :class="[
                      'mt-0.5 grid size-7 shrink-0 place-items-center rounded-[7px]',
                      categoryTones[topic.category],
                    ]"
                  >
                    <Icon :as="categoryIcons[topic.category]" size="inline" />
                  </span>
                  <span class="min-w-0 flex-1">
                    <span class="block text-body font-emphasis text-label">{{ topic.question }}</span>
                    <span class="mt-0.5 block text-caption text-label-tertiary">
                      {{ helpCategoryLabel(topic.category) }}
                    </span>
                  </span>
                  <span
                    class="mt-0.5 grid size-6 shrink-0 place-items-center rounded-full text-label-tertiary transition-colors duration-[120ms] ease-apple group-hover:bg-content group-hover:text-label-secondary"
                  >
                    <Icon
                      :as="ChevronDown"
                      size="inline"
                      class="transition-transform duration-150 ease-apple"
                      :class="expandedIds.has(topic.id) ? 'rotate-180' : ''"
                    />
                  </span>
                </button>
              </h3>

              <div
                v-show="expandedIds.has(topic.id)"
                :id="`help-answer-${topic.id}`"
                role="region"
                :aria-labelledby="`help-question-${topic.id}`"
                class="bg-window/55 px-4 pb-4 pt-1 sm:pl-[60px]"
              >
                <div class="max-w-[72ch] space-y-3 text-body text-label-secondary">
                  <p
                    v-for="paragraph in topic.answer.paragraphs"
                    :key="paragraph"
                    class="break-words leading-5"
                  >
                    {{ paragraph }}
                  </p>

                  <ol v-if="topic.answer.steps" class="space-y-2">
                    <li
                      v-for="(step, stepIndex) in topic.answer.steps"
                      :key="step"
                      class="flex items-start gap-2.5"
                    >
                      <span
                        class="tnum mt-px grid size-5 shrink-0 place-items-center rounded-full bg-content text-caption font-emphasis text-label-secondary shadow-control"
                      >
                        {{ stepIndex + 1 }}
                      </span>
                      <span class="leading-5">{{ step }}</span>
                    </li>
                  </ol>

                  <div
                    v-if="topic.answer.note"
                    class="flex items-start gap-2 rounded-control bg-accent/7 px-3 py-2 text-label"
                  >
                    <Icon :as="Lightbulb" size="inline" class="mt-0.5 shrink-0 text-accent" />
                    <p class="leading-5">{{ topic.answer.note }}</p>
                  </div>

                  <div v-if="topic.answer.links?.length" class="flex flex-wrap gap-2 pt-0.5">
                    <RouterLink
                      v-for="link in topic.answer.links"
                      :key="link.to"
                      :to="link.to"
                      :class="buttonVariants({ variant: 'default', size: 'sm' })"
                    >
                      {{ link.label }}
                      <Icon :as="ArrowRight" size="inline" />
                    </RouterLink>
                  </div>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="mt-3 overflow-hidden rounded-panel bg-content shadow-panel">
            <EmptyState
              title="没有匹配的说明"
              :description="`没有找到包含“${query.trim()}”的内容，可以换个关键词或清除筛选。`"
            >
              <template #icon>
                <Icon :as="SearchX" size="xlarge" class="text-label-tertiary" />
              </template>
              <Button variant="default" size="lg" @click="resetFilters">清除筛选</Button>
            </EmptyState>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

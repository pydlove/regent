<script setup lang="ts">
import { Rocket } from '@lucide/vue'
import { computed, ref, watch } from 'vue'

import Badge from '@/components/ui/badge/Badge.vue'
import Button from '@/components/ui/button/Button.vue'
import Dialog from '@/components/ui/dialog/Dialog.vue'
import Select from '@/components/ui/select/Select.vue'
import { APP_VERSION, CURRENT_RELEASE, RELEASES } from '@/content/releases'
import { shortReleaseVersion } from '@/utils/releaseVersion'

const open = ref(false)
const compactVersion = APP_VERSION.match(/R\d+B\d+/)?.[0] ?? shortReleaseVersion(APP_VERSION)
const selectedVersion = ref(APP_VERSION)
const selectedRelease = computed(
  () => RELEASES.find((release) => release.version === selectedVersion.value) ?? CURRENT_RELEASE,
)
const selectedVersionModel = computed({
  get: () => selectedVersion.value,
  set: (value: string | undefined) => {
    if (value) selectedVersion.value = value
  },
})
const releaseItems = computed(() =>
  RELEASES.map((release) => ({
    value: release.version,
    label: `${shortReleaseVersion(release.version)} · ${release.releasedAt}`,
  })),
)

watch(open, (value) => {
  if (value) selectedVersion.value = APP_VERSION
})

</script>

<template>
  <Dialog
    v-model:open="open"
    layout="form"
    size="large"
    title="版本更新"
    :description="`${selectedRelease.version} · ${selectedRelease.label}`"
    :icon="Rocket"
  >
    <template #trigger>
      <button
        type="button"
        class="tnum rounded-sm text-caption text-label-tertiary transition-colors duration-100 ease-apple hover:text-label-secondary hover:underline hover:underline-offset-2 focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
        :title="`查看 ${APP_VERSION} 与历史版本的更新内容`"
      >
        <span class="sm:hidden">{{ compactVersion }}</span>
        <span class="hidden sm:inline">{{ APP_VERSION }}</span>
      </button>
    </template>

    <div
      class="grid grid-rows-[auto_minmax(0,1fr)] gap-4 overflow-hidden sm:grid-cols-[190px_minmax(0,1fr)] sm:grid-rows-1"
      :style="{ height: 'min(500px, calc(100dvh - 220px))' }"
    >
      <aside class="flex min-h-0 min-w-0 flex-col">
        <p class="text-caption font-emphasis text-label-secondary">版本记录</p>
        <div
          class="scroll-area mt-2 hidden min-h-0 flex-1 space-y-1 overflow-y-auto sm:block"
          role="tablist"
          aria-label="历史版本"
        >
          <button
            v-for="release in RELEASES"
            :id="`release-tab-${release.version}`"
            :key="release.version"
            type="button"
            role="tab"
            :aria-selected="selectedRelease.version === release.version"
            :aria-controls="`release-panel-${release.version}`"
            class="flex w-full flex-col items-start gap-0.5 rounded-control border px-2.5 py-2 text-left transition-colors duration-100 ease-apple focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35"
            :class="
              selectedRelease.version === release.version
                ? 'border-accent/30 bg-accent/10'
                : 'border-transparent hover:bg-hover'
            "
            @click="selectedVersion = release.version"
          >
            <span class="flex w-full items-center gap-1.5">
              <span class="truncate text-caption font-emphasis text-label">
                {{ shortReleaseVersion(release.version) }}
              </span>
              <Badge v-if="release.version === APP_VERSION" tone="accent" class="ml-auto shrink-0">
                当前
              </Badge>
            </span>
            <span class="tnum text-caption text-label-tertiary">{{ release.releasedAt }}</span>
          </button>
        </div>
        <div class="mt-2 sm:hidden">
          <Select v-model="selectedVersionModel" :items="releaseItems" block />
        </div>
      </aside>

      <section
        :id="`release-panel-${selectedRelease.version}`"
        role="tabpanel"
        :aria-labelledby="`release-tab-${selectedRelease.version}`"
        class="scroll-area min-h-0 min-w-0 overflow-y-auto pr-1"
      >
        <div class="rounded-panel bg-content/70 px-3 py-2.5">
          <div class="flex flex-wrap items-center gap-2">
            <Badge tone="accent">{{ selectedRelease.label }}</Badge>
            <span
              v-if="selectedRelease.version === APP_VERSION"
              class="text-caption font-emphasis text-accent"
            >
              当前版本
            </span>
            <span class="tnum ml-auto text-caption text-label-tertiary">
              {{ selectedRelease.releasedAt }}
            </span>
          </div>
          <p class="mt-2 text-caption leading-relaxed text-label-secondary">
            {{ selectedRelease.summary }}
          </p>
        </div>

        <p class="mt-4 text-caption font-emphasis text-label-secondary">主要更新</p>
        <ul class="mt-3 grid gap-x-6 gap-y-3">
          <li
            v-for="item in selectedRelease.highlights"
            :key="item.title"
            class="flex gap-2"
          >
            <span class="mt-[6px] size-1.5 shrink-0 rounded-full bg-accent" aria-hidden="true" />
            <span class="min-w-0">
              <span class="block text-caption font-emphasis text-label">{{ item.title }}</span>
              <span class="mt-0.5 block text-caption leading-relaxed text-label-secondary">
                {{ item.description }}
              </span>
            </span>
          </li>
        </ul>
      </section>
    </div>

    <template #footer>
      <span class="mr-auto self-center text-caption text-label-tertiary">
        收录 {{ RELEASES.length }} 个版本
      </span>
      <Button size="sm" @click="open = false">关闭</Button>
    </template>
  </Dialog>
</template>

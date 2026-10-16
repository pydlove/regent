import { onBeforeUnmount, onMounted, type Ref } from 'vue'

const FADE_DELAY = 800

/**
 * 覆盖式滚动条的淡入靠 [data-scrolling]，而纯 CSS 判断不了「是否正在滚动」，
 * 所以必须由这个 composable 在滚动期间挂上标记，停下来再摘掉。
 */
export function useOverlayScrollbar(target: Ref<HTMLElement | null>): void {
  let timer = 0

  function markScrolling(): void {
    const el = target.value
    if (!el) return
    el.dataset.scrolling = 'true'
    window.clearTimeout(timer)
    timer = window.setTimeout(() => delete el.dataset.scrolling, FADE_DELAY)
  }

  onMounted(() => target.value?.addEventListener('scroll', markScrolling, { passive: true }))
  onBeforeUnmount(() => {
    window.clearTimeout(timer)
    target.value?.removeEventListener('scroll', markScrolling)
  })
}

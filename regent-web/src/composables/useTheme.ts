import { computed, ref } from 'vue'

export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'regent-theme'
const SWITCH_DURATION = 220

/*
 * ?theme=dark / ?theme=light 是给截图和深浅回归用的覆盖。两条规矩：
 *   1. 只在这次加载里生效，且**不写回 localStorage** —— 否则截一次图就把用户偏好改了，
 *      「正式行为看 localStorage」这句话就不成立了；
 *   2. 覆盖生效时仍然允许在界面上切，切了就是切了。
 * README 里写过这个钩子，之前一直没实现，现在补上并且实测过。
 */
const QA_THEME = new URLSearchParams(window.location.search).get('theme')
const hasQaTheme = QA_THEME === 'light' || QA_THEME === 'dark'

const preference = ref<ThemePreference>(readPreference())
const systemDark = ref(window.matchMedia('(prefers-color-scheme: dark)').matches)

function readPreference(): ThemePreference {
  if (hasQaTheme) return QA_THEME as ThemePreference
  const raw = localStorage.getItem(STORAGE_KEY)
  return raw === 'light' || raw === 'dark' ? raw : 'system'
}

function apply(): void {
  const dark = preference.value === 'dark' || (preference.value === 'system' && systemDark.value)
  const root = document.documentElement
  // 只在切换的那一瞬间挂过渡类，避免常驻 transition 拖慢所有交互
  root.classList.add('theme-switching')
  root.classList.toggle('dark', dark)
  window.setTimeout(() => root.classList.remove('theme-switching'), SWITCH_DURATION)
}

/** 三态主题：theme 是偏好（含 system），resolved 是最终生效值。展示层只允许读 resolved。 */
export function useTheme() {
  const resolved = computed<'light' | 'dark'>(() =>
    preference.value === 'system' ? (systemDark.value ? 'dark' : 'light') : preference.value,
  )

  function setTheme(next: ThemePreference): void {
    preference.value = next
    if (!hasQaTheme) localStorage.setItem(STORAGE_KEY, next)
    apply()
  }

  function toggleResolved(): void {
    setTheme(resolved.value === 'dark' ? 'light' : 'dark')
  }

  return { preference, resolved, setTheme, toggleResolved }
}

export function initTheme(): void {
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (event) => {
    systemDark.value = event.matches
    if (preference.value === 'system') apply()
  })
}

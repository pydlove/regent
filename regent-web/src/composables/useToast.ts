import { readonly, ref } from 'vue'

/*
 * 轻量全局提示。刻意不用 Pinia：它没有「状态」可言，只是一条事件流 + 一个队列，
 * 用 store 反而要求每个调用点先 useStore() 再 action，多一层且没有收益。
 *
 * 队列放在模块作用域，所以任何组件 import 到的是同一份 —— 这也是它能在 App.vue
 * 之外的业务代码里直接调用的原因。
 */

export type ToastTone = 'default' | 'success' | 'danger' | 'warning'

export type ToastItem = {
  id: number
  tone: ToastTone
  title: string
  description?: string
}

const DEFAULT_DURATION = 4000

const items = ref<ToastItem[]>([])
let seq = 0
const timers = new Map<number, ReturnType<typeof setTimeout>>()

const dismiss = (id: number) => {
  const timer = timers.get(id)
  if (timer) {
    clearTimeout(timer)
    timers.delete(id)
  }
  items.value = items.value.filter((t) => t.id !== id)
}

const push = (toast: Omit<ToastItem, 'id'>, duration = DEFAULT_DURATION) => {
  const id = ++seq
  items.value = [...items.value, { id, ...toast }]
  // duration <= 0 表示常驻，必须手动关：留给「用户必须读到、不能自己消失」的提示
  if (duration > 0) timers.set(id, setTimeout(() => dismiss(id), duration))
  return id
}

export function useToast() {
  return {
    items: readonly(items),
    dismiss,
    show: (title: string, options: Omit<ToastItem, 'id' | 'title' | 'tone'> & { tone?: ToastTone } = {}) =>
      push({ ...options, tone: options.tone ?? 'default', title }),
    success: (title: string, description?: string) =>
      push({ tone: 'success', title, description }),
    failure: (title: string, description?: string) =>
      push({ tone: 'danger', title, description }),
    warning: (title: string, description?: string) =>
      push({ tone: 'warning', title, description }),
  }
}

import { ref } from 'vue'

import type { Project } from '@/types/api'

/*
 * 「注册项目」弹框的开关。
 *
 * 它有三处入口：工具栏（项目下拉旁边）、项目页的按钮、以及新建任务弹框里「一个项目都还没有」
 * 时的提示按钮。三处都要能开同一个弹框，所以状态必须是共享的。
 *
 * 用模块级的 ref 而不是 pinia store：这里只有一个布尔值，没有要拉的数据、没有要跟着列表
 * 失效的计算属性 —— 建一个 store 只会多一层「为什么这件事在 store 里」的追问。
 */
const open = ref(false)

/**
 * 最近一次注册成功的项目。
 *
 * 弹框在全应用只有一个实例（挂在 App 上），而项目页要「知道刚建了什么」才能把它选中 ——
 * 靠事件传不过去，所以把结果留在这里。它是个通知位，不是缓存：项目列表仍在任务 store 里。
 */
const lastRegistered = ref<Project | null>(null)

export function useProjectDialog() {
  return {
    open,
    lastRegistered,
    openDialog: () => {
      open.value = true
    },
  }
}

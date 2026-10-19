import { ref } from 'vue'

const open = ref(false)

/** 全站共享的 Codex 安装弹框开关。 */
export function useCodexInstallDialog() {
  const openDialog = () => {
    open.value = true
  }
  const closeDialog = () => {
    open.value = false
  }
  return { open, openDialog, closeDialog }
}

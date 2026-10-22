type ClipboardEnvironment = {
  clipboard?: Pick<Clipboard, 'writeText'> | null
  document?: Document | null
}

/**
 * 优先使用异步剪贴板 API；局域网 HTTP 等不安全上下文没有该 API 时，
 * 退回临时文本框，保证桌面端和手机浏览器都能复制。
 */
export async function copyTextToClipboard(
  text: string,
  environment: ClipboardEnvironment = {
    clipboard: globalThis.navigator?.clipboard,
    document: globalThis.document,
  },
): Promise<boolean> {
  if (!text) return false

  try {
    if (environment.clipboard?.writeText) {
      await environment.clipboard.writeText(text)
      return true
    }
  } catch {
    // 浏览器拒绝剪贴板权限时继续尝试兼容方案
  }

  if (!environment.document) return false

  const field = environment.document.createElement('textarea')
  try {
    field.value = text
    field.setAttribute('readonly', '')
    field.className = 'fixed left-[-9999px] top-0'
    environment.document.body.appendChild(field)
    field.select()
    return environment.document.execCommand('copy')
  } catch {
    return false
  } finally {
    field.remove()
  }
}

function normalizePath(value: string): string {
  const normalized = value.trim().replaceAll('\\', '/').replace(/\/+$/, '')
  return normalized.startsWith('//?/') ? normalized.slice(4) : normalized
}

function comparablePath(value: string): string {
  return /^[a-zA-Z]:\//.test(value) ? value.toLowerCase() : value
}

/**
 * 把 Electron 选中的脚本绝对路径转换为项目根目录内的相对路径。
 * 浏览器拿不到绝对路径、脚本不在项目内或输入不完整时，退回文件名。
 */
export function resolveProjectScriptPath(
  filename: string,
  filePath: string | null | undefined,
  workspacePath: string | null | undefined,
): string {
  const fallback = filename.trim().replaceAll('\\', '/').replace(/^\/+/, '')
  const file = normalizePath(filePath ?? '')
  const root = normalizePath(workspacePath ?? '')
  if (!file || !root) return fallback

  const comparableFile = comparablePath(file)
  const comparableRoot = comparablePath(root)
  if (comparableFile === comparableRoot) return fallback
  if (!comparableFile.startsWith(`${comparableRoot}/`)) return fallback
  return file.slice(root.length + 1)
}

/**
 * 识别脚本是否应由本机执行完整发布流程。
 *
 * 传统脚本通过 deploy-local.sh / REGENT_SERVER_IP 标记；
 * 新的双模式脚本直接实现 run_local/run_remote，并由 -H/--host 选择本地模式。
 */
export function isProjectReleaseScript(content: string | null | undefined): boolean {
  const script = content ?? ''
  if (script.includes('deploy-local.sh') && script.includes('REGENT_SERVER_IP')) {
    return true
  }
  return (
    /^\s*run_local\s*\(\)\s*\{/m.test(script) &&
    /^\s*run_remote\s*\(\)\s*\{/m.test(script) &&
    (script.includes('-H/--host') || script.includes('--host'))
  )
}

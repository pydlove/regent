import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/*
 * UI 约定的机器可验证部分。跑在 typecheck 里，违反即失败。
 *
 * 现在只有一条，但它是拿一次静默失效换来的：
 *   Tailwind v4 把字号和文字颜色都生成在 `text-` 前缀下，tailwind-merge 只按前缀猜分组，
 *   会把 `text-label`（颜色）和 `text-control`（字号）当成同一组冲突类，
 *   后写的直接删掉先写的，且不报错。修法是给 twMerge 配上正确的 font-size 组，
 *   而那个配置只存在于 src/components/ui/tv.ts 里。
 *   所以任何 `*.variants.ts` 直接 import 'tailwind-variants' 都会绕开配置，重新踩坑。
 */

const ROOT = new URL('..', import.meta.url).pathname
const SRC = join(ROOT, 'src')
const ALLOWED = join(SRC, 'components/ui/tv.ts')

const walk = (dir) =>
  readdirSync(dir).flatMap((name) => {
    const full = join(dir, name)
    return statSync(full).isDirectory() ? walk(full) : [full]
  })

const problems = []

for (const file of walk(SRC)) {
  if (!/\.(ts|vue)$/.test(file)) continue
  const text = readFileSync(file, 'utf8')
  const rel = relative(ROOT, file)

  if (file !== ALLOWED && /from\s+['"]tailwind-variants['"]/.test(text)) {
    if (/import\s*\{[^}]*\btv\b[^}]*\}\s*from\s+['"]tailwind-variants['"]/.test(text)) {
      problems.push(
        `${rel}: 不要从 'tailwind-variants' 直接取 tv，改成 import { tv } from '@/components/ui/tv'。` +
          `绕开自定义 twMergeConfig 会让 text-<字号> 与 text-<颜色> 互相吞并，静默失效。`,
      )
    }
  }

  // 同一个 class 串里同时出现字号和颜色令牌时，提醒作者这两组靠 tv.ts 的配置区分
  if (file === ALLOWED) continue
  const sizeTokens = ['text-caption', 'text-body', 'text-control', 'text-metric']
  const colorTokens = /\btext-(label|label-secondary|label-tertiary|accent|danger|warning|success|neutral|white)\b/
  const hasSize = sizeTokens.some((t) => text.includes(t))
  if (hasSize && colorTokens.test(text) && /tv\(/.test(text)) {
    const hasSharedImport = text.includes("from '@/components/ui/tv'")
    if (!hasSharedImport) {
      problems.push(`${rel}: 同时用到了字号与颜色令牌，但没走共享的 tv，见 tv.ts 的说明。`)
    }
  }
}

if (problems.length > 0) {
  console.error('UI 约定检查未通过：\n')
  for (const p of problems) console.error('  ✗ ' + p)
  console.error('')
  process.exit(1)
}
console.log('UI 约定检查通过')

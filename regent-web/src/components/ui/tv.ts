import { createTV } from 'tailwind-variants'

/*
 * 全局 tv 实例。所有 `*.variants.ts` 必须从这里 import，不要直接 import 'tailwind-variants'。
 *
 * 为什么必须自定义 twMergeConfig：
 *   Tailwind v4 把「字号」和「文字颜色」都放在 `text-` 前缀下（`--text-control` → text-control，
 *   `--color-label` → text-label）。而 tailwind-merge 只按前缀猜分组，遇到我们自造的
 *   `label` / `control` 这类值它一律归到「文字颜色」组，于是 `text-label text-control`
 *   被当成一对冲突类，**后写的把先写的删掉**，而且不报任何错：
 *
 *     tv({ base: 'text-label text-control' })  →  'text-control'   // 颜色没了
 *     tv({ base: 'text-control', variants: { v: { a: 'text-label' } } })
 *                                              →  'text-label'     // 字号没了
 *
 *   实测后果：Input 的文字颜色其实是靠继承 body 的 color 蒙对的，放进任何
 *   色彩上下文不一的容器（比如基准页的 .dark 样板）就变成黑字；Button 的字号同理，
 *   放进 text-caption 里会跟着缩成 11px。
 *
 * 这里明确告诉 tailwind-merge 哪几个值属于字号组，两组就不再互相吞并。
 * 新增字号令牌时记得同步这张表 —— 漏了就是上面那种静默失效。
 */
export const tv = createTV({
  twMergeConfig: {
    extend: {
      classGroups: {
        'font-size': [{ text: ['caption', 'body', 'control', 'metric'] }],
      },
    },
  },
})

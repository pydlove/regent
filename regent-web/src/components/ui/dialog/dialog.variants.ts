import type { Component } from 'vue'

import { tv } from '@/components/ui/tv'

/**
 * 对话框的视觉全在这里，`Dialog.vue`（模态壳）与基准页的静态样板共用同一份，
 * 所以样板上看到的样子就是真实弹出的样子。见 FRONTEND.md 第十四节。
 *
 * 几点是照着 NSAlert 定的，不是拍脑袋：
 *   - 文字左对齐。macOS 的警告框不是 iOS 那种居中弹窗，标题与说明都靠左。
 *   - 图标（若给）在标题左侧，与标题同一行，作为紧凑的上下文标识。
 *   - 按钮在右下角，并且「取消在左、默认动作在右」。破坏性动作才用红色实心。
 */
export const dialogPanelVariants = tv({
  /*
   * 宽高都兜住视口：日志和审批弹框在手机上不能横向溢出；正文再长也只滚中间区域，
   * 标题与底部操作按钮不能被长内容顶出屏幕。
   */
  base:
    'flex max-w-[calc(100vw-24px)] flex-col overflow-hidden ' +
    'bg-material backdrop-blur-[30px] backdrop-saturate-150 shadow-window animate-panel-in rounded-window p-5',
  variants: {
    maxHeight: {
      default: 'max-h-[calc(100dvh-24px)]',
      compact: 'max-h-[80dvh]',
    },
    height: {
      auto: '',
      fixed: 'h-[760px]',
    },
    fullscreen: {
      true: 'h-dvh w-screen max-h-none max-w-none rounded-none',
      false: '',
    },
    layout: {
      /* 问「要不要做」：窄一点，一行说明就够 */
      alert: 'w-[340px]',
      /* 放表单，即「怎么做」：宽度交给 size 决定，见下 */
      form: 'w-96',
      /* 放日志与事件流：再宽一档。高度不在这里限 —— 让内容自己带 max-height 滚动，
         面板就不会出现「按钮被长日志顶出屏幕」这种要命的问题 */
      log: 'w-[640px]',
    },
    /*
     * 宽度档位：regular 够放短输入；large 留给一般长表单；wide 留给复杂录入和日志弹框。
     *
     * alert 与 form 不允许被 wide 撑开，log 的 large/wide 各自有明确宽度；
     * compoundVariants 负责把这些组合钉回预设，避免调用点凭感觉改宽。
     */
    size: {
      regular: '',
      large: 'w-[640px]',
      wide: 'w-[760px]',
      xwide: 'w-[960px]',
    },
  },
  compoundVariants: [
    { layout: 'alert', size: 'large', class: 'w-[340px]' },
    { layout: 'alert', size: 'wide', class: 'w-[340px]' },
    { layout: 'alert', size: 'xwide', class: 'w-[340px]' },
    { layout: 'form', size: 'wide', class: 'w-[760px]' },
    { layout: 'form', size: 'xwide', class: 'w-[960px]' },
    { layout: 'log', size: 'large', class: 'w-[640px]' },
    { layout: 'log', size: 'wide', class: 'w-[760px]' },
    { layout: 'log', size: 'xwide', class: 'w-[960px]' },
  ],
  defaultVariants: {
    layout: 'alert',
    size: 'regular',
    maxHeight: 'default',
    height: 'auto',
    fullscreen: false,
  },
})

/*
 * 窗口红黄绿，弹框标题栏上的那三颗。
 *
 * 弹框在本站里就是一个「窗口」，所以它带标题栏的按钮 —— 这是 macOS 上「独立窗口，不是网页
 * 浮层」最直接的信号，也是「苹果风」被一眼认出来的地方。
 *
 * 三颗里只有关闭能用：模态窗口不能最小化、也不能缩放（AppKit 在同样的窗口上把另外两颗
 * 画成灰色）。所以黄绿是**装饰** —— 不进 tab 序列、不响应点击，不是点了没反应的假按钮。
 * 它们保留本色、只用透明度表示不可用，而不是涂成纯灰：浅色面板上的灰点会被当成没渲染出来。
 */
export const dialogTrafficLightVariants = tv({
  base: 'size-3 rounded-full',
  variants: {
    tone: {
      close: 'bg-traffic-close',
      minimize: 'bg-traffic-minimize opacity-50',
      zoom: 'bg-traffic-zoom opacity-50',
    },
  },
  defaultVariants: { tone: 'close' },
})

/* 关闭那颗是按钮：12px 的圆里还要居中一个 6px 的 ×（悬停时出现，与 macOS 一致） */
export const dialogTrafficCloseVariants = tv({
  extend: dialogTrafficLightVariants,
  base: 'flex items-center justify-center',
})

export const dialogTitleClass = 'text-body font-emphasis text-label'
export const dialogDescriptionClass = 'mt-1 text-caption text-label-secondary'

/** 图标只做色调区分，不给每个调用点单独配颜色 */
export const dialogIconVariants = tv({
  variants: {
    tone: {
      default: 'text-accent',
      danger: 'text-danger',
    },
  },
  defaultVariants: { tone: 'default' },
})

export type DialogVariantProps = NonNullable<Parameters<typeof dialogPanelVariants>[0]>
export type DialogIconProps = NonNullable<Parameters<typeof dialogIconVariants>[0]>
export type DialogIcon = Component

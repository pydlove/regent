import { tv } from '@/components/ui/tv'

/**
 * 按钮变体。所有外观差异只能加在这里的 variants 里，业务组件不得覆盖内边距/圆角/字号。
 * 见 FRONTEND.md 第五节。
 */
export const buttonVariants = tv({
  base:
    'inline-flex shrink-0 select-none items-center justify-center gap-1.5 whitespace-nowrap ' +
    'text-control ' +
    'rounded-control font-normal transition-colors duration-[120ms] ease-apple ' +
    'focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-accent/35 ' +
    'disabled:pointer-events-none disabled:opacity-40 active:scale-[0.98]',
  variants: {
    variant: {
      default: 'bg-content text-label shadow-control hover:brightness-[0.98]',
      // 强调按钮加一点内部高光，纯色块会显得扁平；这是 macOS 按钮「可点」的观感来源之一
      primary:
        'bg-accent text-white shadow-[inset_0_0.5px_0_rgba(255,255,255,0.25)] hover:brightness-105',
      danger:
        'bg-danger text-white shadow-[inset_0_0.5px_0_rgba(255,255,255,0.25)] hover:brightness-105',
      ghost: 'bg-transparent text-label hover:bg-hover',
    },
    size: {
      sm: 'h-control-sm px-2',
      regular: 'h-control px-3',
      lg: 'h-control-lg px-4',
    },
    block: { true: 'w-full', false: '' },
  },
  defaultVariants: { variant: 'default', size: 'regular', block: false },
})

export type ButtonVariantProps = NonNullable<Parameters<typeof buttonVariants>[0]>

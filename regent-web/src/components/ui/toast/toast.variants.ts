import { tv } from '@/components/ui/tv'

export const toastVariants = tv({
  base:
    'pointer-events-auto flex w-80 items-start gap-2.5 rounded-panel bg-material p-3 ' +
    'shadow-popover backdrop-blur-[30px] backdrop-saturate-150',
})

/** 小色点而不是彩色胶囊或整块底色：和列表里的等级标记同一套语言 */
export const toastDotVariants = tv({
  base: 'mt-1.5 size-1.5 shrink-0 rounded-full',
  variants: {
    tone: {
      default: 'bg-neutral',
      success: 'bg-success',
      warning: 'bg-warning',
      danger: 'bg-danger',
    },
  },
  defaultVariants: { tone: 'default' },
})

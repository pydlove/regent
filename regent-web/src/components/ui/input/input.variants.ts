import { tv } from '@/components/ui/tv'

export const inputVariants = tv({
  base:
    'w-full rounded-control border border-field-border bg-field px-2 text-label text-control ' +
    'shadow-[inset_0_1px_2px_rgba(0,0,0,0.04)] ' +
    'placeholder:text-label-tertiary ' +
    'transition-[border-color,box-shadow] duration-[120ms] ease-apple ' +
    'focus-visible:outline-none focus-visible:border-accent focus-visible:ring-3 focus-visible:ring-accent/20 ' +
    'disabled:opacity-40',
  variants: {
    /* size 自己不产出类，只用来驱动 compoundVariants 里的高度。
       高度不能写在这里：单行要 h-control、多行要 h-auto，两个同时挂在元素上时
       tailwind-merge 认不出我们自定义的 spacing 刻度是一对冲突类，谁赢取决于 Tailwind
       的产物顺序 —— 实测下来 h-control 赢，Textarea 的 rows 直接失效。
       从根上不产生冲突，比事后调顺序可靠。 */
    size: {
      sm: '',
      regular: '',
      lg: '',
      xl: '',
    },
    invalid: {
      true: 'border-danger',
      false: '',
    },
    /* 多行输入不能有固定高度，行高交给 rows；内边距比单行略大一档，
       否则第一行文字会贴上边框 */
    multiline: {
      true: 'h-auto resize-none py-1.5',
      false: '',
    },
  },
  compoundVariants: [
    { multiline: false, size: 'sm', class: 'h-control-sm' },
    { multiline: false, size: 'regular', class: 'h-control' },
    { multiline: false, size: 'lg', class: 'h-control-lg' },
    { multiline: false, size: 'xl', class: 'h-control-xl' },
  ],
  defaultVariants: { size: 'regular', invalid: false, multiline: false },
})

export type InputVariantProps = NonNullable<Parameters<typeof inputVariants>[0]>

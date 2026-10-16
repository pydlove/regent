import { tv } from '@/components/ui/tv'

export const badgeVariants = tv({
  base:
    'inline-flex items-center gap-1 rounded-badge px-1.5 py-px text-caption font-emphasis ' +
    'group-data-[selected=true]:bg-white/20 group-data-[selected=true]:text-white',
  variants: {
    tone: {
      neutral: 'bg-neutral/15 text-neutral',
      accent: 'bg-accent/15 text-accent',
      success: 'bg-success/15 text-success',
      warning: 'bg-warning/15 text-warning',
      danger: 'bg-danger/15 text-danger',
    },
  },
  defaultVariants: { tone: 'neutral' },
})

export type BadgeVariantProps = NonNullable<Parameters<typeof badgeVariants>[0]>

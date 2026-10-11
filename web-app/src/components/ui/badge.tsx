import { cva, type VariantProps } from 'class-variance-authority';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

// A small rubber mark pressed beside a line: pencil for what is still provisional, violet for what is about
// to be posted.
const badgeVariants = cva(
  'inline-flex shrink-0 items-center rounded-sm border px-1.5 py-px font-sans text-xs leading-4 font-medium',
  {
    variants: {
      tone: {
        pencil: 'border-pencil/55 text-pencil',
        stamp: 'border-stamp/70 text-stamp',
      },
    },
    defaultVariants: {
      tone: 'pencil',
    },
  },
);

export type BadgeProps = ComponentProps<'span'> & VariantProps<typeof badgeVariants>;

export function Badge({ className, tone, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />;
}

import { cva, type VariantProps } from 'class-variance-authority';
import { Slot } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export const buttonVariants = cva(
  'inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium outline-none transition-[color,background-color,border-color,transform] duration-150 focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-paper active:translate-y-px disabled:pointer-events-none disabled:opacity-45',
  {
    variants: {
      variant: {
        default: 'bg-ink text-paper hover:bg-ink/85',
        // The teller's stamp: the one action that posts. A second rule inset from the edge is the rubber
        // stamp's own frame.
        stamp:
          'bg-stamp text-[0.9375rem] font-semibold text-stamp-foreground outline outline-1 -outline-offset-[5px] outline-stamp-foreground/55 hover:bg-stamp/90',
        outline: 'border border-rule-strong bg-transparent text-ink hover:bg-ink/[0.04]',
        ghost: 'text-ink hover:bg-ink/[0.06]',
        // Controls printed on the cover: foil on cloth.
        cover:
          'text-foil/85 hover:bg-black/15 hover:text-foil focus-visible:ring-foil focus-visible:ring-offset-cover',
      },
      size: {
        default: 'h-10 px-4',
        // A value written on a form line: no box of its own, since the line's rule already is one.
        field:
          'h-10 w-full justify-between rounded-sm px-2 text-left font-normal hover:bg-ink/[0.05]',
        icon: 'h-10 w-10',
        key: 'h-12 px-6',
      },
    },
    defaultVariants: {
      variant: 'default',
      size: 'default',
    },
  },
);

export type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean;
  };

export function Button({ className, variant, size, asChild = false, ...props }: ButtonProps) {
  const Comp = asChild ? Slot.Root : 'button';
  return <Comp className={cn(buttonVariants({ variant, size }), className)} {...props} />;
}

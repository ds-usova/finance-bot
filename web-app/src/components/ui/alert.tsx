import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export function Alert({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      role="alert"
      className={cn(
        // `bg-card`, not `bg-surface`: an alert is a block on the page like the filter panel and the day
        // sections, and one the colour of the page behind it reads as a gap rather than as a block.
        'flex w-full items-start gap-2.5 rounded-xl border border-border bg-card px-4 py-3 text-sm text-foreground shadow-sm',
        className,
      )}
      {...props}
    />
  );
}

export function AlertDescription({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('text-sm', className)} {...props} />;
}

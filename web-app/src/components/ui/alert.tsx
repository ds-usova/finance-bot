import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export function Alert({ className, ...props }: ComponentProps<'div'>) {
  return (
    <div
      role="alert"
      className={cn(
        'w-full rounded-md border border-border bg-surface px-4 py-3 text-sm text-foreground',
        className,
      )}
      {...props}
    />
  );
}

export function AlertDescription({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('text-sm', className)} {...props} />;
}

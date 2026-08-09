import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export type BadgeProps = ComponentProps<'span'>;

export function Badge({ className, ...props }: BadgeProps) {
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center rounded-full bg-pending-surface px-2 py-0.5 text-xs font-medium text-pending',
        className,
      )}
      {...props}
    />
  );
}

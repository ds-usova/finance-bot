import { Check, Minus } from 'lucide-react';
import { Checkbox as CheckboxPrimitive } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        // The box is 16px because that is what reads well beside 14px text, but 16px is far too small to hit.
        // The `before` pseudo-element carries the press out to 32px in every direction without moving anything
        // on screen, and `z-10` puts it over whatever follows it in the DOM — beside a day's tick that is the
        // accordion trigger, so a near miss used to open the day instead of clearing the tick.
        'relative z-10 flex h-4 w-4 shrink-0 items-center justify-center rounded-sm border border-border outline-none transition-colors before:absolute before:-inset-2 before:content-[""] focus-visible:ring-2 focus-visible:ring-accent data-[state=checked]:border-accent data-[state=checked]:bg-accent data-[state=indeterminate]:border-accent data-[state=indeterminate]:bg-accent disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center text-surface">
        {props.checked === 'indeterminate' ? (
          <Minus className="h-3.5 w-3.5" />
        ) : (
          <Check className="h-3.5 w-3.5" />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

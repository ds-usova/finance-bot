import { Check, Minus } from 'lucide-react';
import { Checkbox as CheckboxPrimitive } from 'radix-ui';
import type { ComponentProps } from 'react';
import { cn } from '../../lib/utils';

export function Checkbox({ className, ...props }: ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      className={cn(
        // The box is 18px because that is what reads well beside the line's text, but 18px is far too small to
        // hit. The `before` pseudo-element carries the press out to 44px without moving anything on screen, and
        // `z-10` puts it over whatever follows it in the DOM — beside a day's tick that is the accordion
        // trigger, so a near miss used to open the day instead of clearing the tick.
        'relative z-10 flex h-[18px] w-[18px] shrink-0 cursor-pointer items-center justify-center rounded-sm border-[1.5px] border-pencil bg-leaf outline-none transition-colors before:absolute before:-inset-[13px] before:content-[""] hover:border-ink focus-visible:ring-2 focus-visible:ring-focus focus-visible:ring-offset-1 focus-visible:ring-offset-paper data-[state=checked]:border-stamp data-[state=checked]:bg-stamp data-[state=indeterminate]:border-stamp data-[state=indeterminate]:bg-stamp disabled:cursor-not-allowed disabled:opacity-40',
        className,
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator className="flex items-center justify-center text-stamp-foreground">
        {props.checked === 'indeterminate' ? (
          <Minus className="h-3.5 w-3.5" strokeWidth={3} />
        ) : (
          <Check className="h-3.5 w-3.5" strokeWidth={3} />
        )}
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  );
}

import { ChevronDown } from 'lucide-react';
import { Accordion as AccordionPrimitive } from 'radix-ui';
import type { ComponentProps, ReactNode } from 'react';
import { cn } from '../../lib/utils';

export const Accordion = AccordionPrimitive.Root;
export const AccordionItem = AccordionPrimitive.Item;

export function AccordionTrigger({
  className,
  children,
  leading,
  ...props
}: ComponentProps<typeof AccordionPrimitive.Trigger> & {
  /** A control that must sit beside the trigger rather than inside it, since the Radix `Trigger` is itself a
   * `<button>` and a control among its children would be a control inside a button. Rendered before the
   * trigger, over the leading gutter the entry rows below already reserve. */
  leading?: ReactNode;
}) {
  return (
    // The hover layer is the header's, not the trigger's: a leading control sits outside the trigger, so a
    // highlight painted by the trigger alone stops short of it and the row reads as two pieces.
    <AccordionPrimitive.Header className="flex items-center rounded-xl transition-colors hover:bg-muted/50">
      {leading}
      <AccordionPrimitive.Trigger
        className={cn(
          'flex flex-1 cursor-pointer items-center justify-between gap-2 rounded-xl py-4 text-left text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-accent [&[data-state=open]>svg]:rotate-180',
          className,
        )}
        {...props}
      >
        {/* Ahead of the content, not after it: anything between the trigger's last child and its padding
            edge insets that child from the edge, and then nothing in the panel below can line up with it. */}
        <ChevronDown className="h-4 w-4 shrink-0 text-muted-foreground transition-transform duration-200" />
        {children}
      </AccordionPrimitive.Trigger>
    </AccordionPrimitive.Header>
  );
}

export function AccordionContent({
  className,
  children,
  ...props
}: ComponentProps<typeof AccordionPrimitive.Content>) {
  return (
    <AccordionPrimitive.Content
      className="overflow-hidden text-sm data-[state=closed]:animate-accordion-up data-[state=open]:animate-accordion-down"
      {...props}
    >
      <div className={cn('pt-0 pb-4', className)}>{children}</div>
    </AccordionPrimitive.Content>
  );
}

import { ChevronLeft, ChevronRight } from 'lucide-react';
import { DayPicker, type DayPickerProps } from 'react-day-picker';
import { cn } from '../../lib/utils';

export type CalendarProps = DayPickerProps;

export function Calendar({ className, classNames, ...props }: CalendarProps) {
  return (
    <DayPicker
      className={cn('text-sm', className)}
      classNames={{
        months: 'flex flex-col gap-4 sm:flex-row',
        month: 'flex flex-col gap-3',
        month_caption: 'flex h-8 items-center justify-center',
        caption_label: 'font-medium',
        nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
        button_previous:
          'inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-40',
        button_next:
          'inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground hover:bg-muted hover:text-foreground disabled:opacity-40',
        month_grid: 'w-full border-collapse',
        weekdays: 'flex',
        weekday: 'w-9 text-xs font-normal text-muted-foreground',
        week: 'mt-1 flex w-full',
        day: 'h-9 w-9 p-0 text-center',
        day_button:
          'h-9 w-9 cursor-pointer rounded-md font-normal transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-40',
        today: 'font-semibold text-accent',
        outside: 'text-muted-foreground/50',
        selected: 'bg-accent/15',
        range_start:
          'rounded-l-md bg-accent/15 [&>button]:bg-accent [&>button]:text-accent-foreground',
        range_end:
          'rounded-r-md bg-accent/15 [&>button]:bg-accent [&>button]:text-accent-foreground',
        range_middle: 'bg-accent/15',
        hidden: 'invisible',
        ...classNames,
      }}
      components={{
        // The bundled chevron is one component for both directions, told apart by its orientation.
        Chevron: ({ orientation }) =>
          orientation === 'left' ? (
            <ChevronLeft className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          ),
      }}
      {...props}
    />
  );
}

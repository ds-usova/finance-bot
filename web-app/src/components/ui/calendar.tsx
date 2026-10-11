import { ChevronLeft, ChevronRight } from 'lucide-react';
import { DayPicker, type DayPickerProps } from 'react-day-picker';
import { cn } from '../../lib/utils';

export type CalendarProps = DayPickerProps;

const NAV_BUTTON =
  'inline-flex h-9 w-9 cursor-pointer items-center justify-center rounded-sm text-pencil hover:bg-ink/[0.06] hover:text-ink disabled:opacity-40';

export function Calendar({ className, classNames, ...props }: CalendarProps) {
  return (
    <DayPicker
      className={cn('relative text-sm', className)}
      classNames={{
        months: 'flex flex-col gap-4 sm:flex-row sm:gap-6',
        month: 'flex flex-col gap-3',
        month_caption: 'flex h-9 items-center justify-center',
        caption_label: 'font-semibold',
        nav: 'absolute inset-x-0 top-0 flex items-center justify-between',
        button_previous: NAV_BUTTON,
        button_next: NAV_BUTTON,
        month_grid: 'w-full border-collapse',
        weekdays: 'flex border-b border-rule',
        weekday: 'column-head w-10 pb-1.5 font-semibold',
        week: 'mt-1 flex w-full',
        day: 'h-10 w-10 p-0 text-center font-mono',
        day_button:
          'h-10 w-10 cursor-pointer rounded-sm transition-colors hover:bg-ink/[0.06] focus-visible:ring-2 focus-visible:ring-focus focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-40',
        today: 'font-bold underline decoration-rule-strong underline-offset-4',
        outside: 'text-pencil/60',
        selected: 'bg-cover/12',
        range_start: 'rounded-l-sm bg-cover/12 [&>button]:bg-cover [&>button]:text-foil',
        range_end: 'rounded-r-sm bg-cover/12 [&>button]:bg-cover [&>button]:text-foil',
        range_middle: 'bg-cover/12',
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

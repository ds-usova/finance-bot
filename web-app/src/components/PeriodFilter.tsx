import { CalendarDays, X } from 'lucide-react';
import { useState } from 'react';
import type { DateRange } from 'react-day-picker';
import { useTranslation } from 'react-i18next';
import { Button } from './ui/button';
import { Calendar } from './ui/calendar';
import { Label } from './ui/label';
import { Popover, PopoverContent, PopoverTrigger } from './ui/popover';

export type Period = {
  from?: string;
  to?: string;
};

export type PeriodFilterProps = {
  period: Period;
  /** Called only with a complete pair or with neither day — the ledger refuses a lone one with 400. */
  onChange: (period: Period) => void;
};

/** The ledger's day, `YYYY-MM-DD`, read off the calendar's own day rather than through an instant. */
function toDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${String(date.getFullYear())}-${month}-${day}`;
}

function fromDay(day: string | undefined): Date | undefined {
  if (day === undefined) {
    return undefined;
  }
  const [year, month, date] = day.split('-').map(Number);
  if (year === undefined || month === undefined || date === undefined) {
    return undefined;
  }
  return new Date(year, month - 1, date);
}

function toRange(period: Period): DateRange | undefined {
  const from = fromDay(period.from);
  if (from === undefined) {
    return undefined;
  }
  return { from, to: fromDay(period.to) };
}

export function PeriodFilter({ period, onChange }: PeriodFilterProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const [open, setOpen] = useState(false);
  // The half-picked range lives here: the callback above only fires on a complete pair, so the prop cannot
  // hold the first day on its own and the calendar would forget it between clicks.
  const [range, setRange] = useState<DateRange | undefined>(() => toRange(period));

  const label = (day: string) =>
    new Intl.DateTimeFormat(locale, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    }).format(fromDay(day));

  const settled = period.from !== undefined && period.to !== undefined;
  const triggerLabel = settled
    ? `${label(period.from ?? '')} – ${label(period.to ?? '')}`
    : t('filters.periodAny');

  const commit = (next: Period) => {
    setRange(toRange(next));
    onChange(next);
    setOpen(false);
  };

  const select = (next: DateRange | undefined) => {
    setRange(next);
    if (next?.from && next.to) {
      commit({ from: toDay(next.from), to: toDay(next.to) });
    }
  };

  const clear = () => {
    commit({});
  };

  const preset = (from: Date, to: Date) => {
    commit({ from: toDay(from), to: toDay(to) });
  };

  const today = new Date();
  const presets: { key: string; apply: () => void }[] = [
    {
      key: 'filters.presetThisMonth',
      apply: () => preset(new Date(today.getFullYear(), today.getMonth(), 1), today),
    },
    {
      key: 'filters.presetLastThirtyDays',
      apply: () =>
        preset(new Date(today.getFullYear(), today.getMonth(), today.getDate() - 29), today),
    },
    {
      key: 'filters.presetThisYear',
      apply: () => preset(new Date(today.getFullYear(), 0, 1), today),
    },
  ];

  return (
    <div className="flex min-w-0 flex-col gap-1.5 sm:col-span-3">
      <Label id="filter-period-label">{t('filters.period')}</Label>
      <div className="flex items-center gap-2">
        <Popover open={open} onOpenChange={setOpen}>
          <PopoverTrigger asChild>
            <Button
              variant="outline"
              aria-labelledby="filter-period-label filter-period-value"
              className="h-9 flex-1 justify-start gap-2 font-normal"
            >
              <CalendarDays aria-hidden="true" className="h-4 w-4 text-muted-foreground" />
              <span id="filter-period-value">{triggerLabel}</span>
            </Button>
          </PopoverTrigger>
          <PopoverContent className="flex w-auto flex-col gap-3 sm:flex-row">
            <div className="flex shrink-0 flex-row gap-1 sm:w-36 sm:flex-col">
              {presets.map(({ key, apply }) => (
                <Button
                  key={key}
                  variant="ghost"
                  className="justify-start font-normal"
                  onClick={apply}
                >
                  {t(key as 'filters.presetThisMonth')}
                </Button>
              ))}
              <Button variant="ghost" className="justify-start font-normal" onClick={clear}>
                {t('filters.presetAllTime')}
              </Button>
            </div>
            <Calendar
              mode="range"
              resetOnSelect
              showOutsideDays={false}
              numberOfMonths={2}
              defaultMonth={range?.from}
              selected={range}
              onSelect={select}
            />
          </PopoverContent>
        </Popover>
        {settled && (
          <Button variant="ghost" size="icon" aria-label={t('filters.periodClear')} onClick={clear}>
            <X aria-hidden="true" className="h-4 w-4" />
          </Button>
        )}
      </div>
    </div>
  );
}

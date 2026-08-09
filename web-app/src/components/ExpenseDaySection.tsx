import { useTranslation } from 'react-i18next';
import type { RenderedMoney } from '../api/expenses';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from './ui/accordion';
import { Badge } from './ui/badge';
import { relativeDay, type ExpenseDay } from './expenseDays';

export type ExpenseDaySectionProps = {
  day: ExpenseDay;
  categoryNames: Map<number, string>;
  /** The ids of ticked `PENDING` entries, owned by the page. */
  tickedIds: ReadonlySet<number>;
  /** One entry ticked or unticked. */
  onTick: (id: number, ticked: boolean) => void;
  /** A whole day ticked or unticked (D18). */
  onTickDay: (ids: number[], ticked: boolean) => void;
  /** 100 are ticked, so every unticked checkbox is disabled (Q1). */
  atBound: boolean;
};

function formatMoney(money: RenderedMoney): string {
  return `${money.currency}${money.separator}${money.amount}`;
}

export function ExpenseDaySection({
  day,
  categoryNames,
  tickedIds,
  onTick,
  onTickDay,
  atBound,
}: ExpenseDaySectionProps) {
  // Stub: the checkboxes GU03 adds read tickedIds, call onTick and onTickDay, and respect atBound. This pass
  // only threads the props through so the component still type-checks.
  void tickedIds;
  void onTick;
  void onTickDay;
  void atBound;
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';

  const relative = relativeDay(day.day, new Date());
  const dayLabel = relative
    ? t(`listing.${relative}`)
    : // The day is a UTC day, so it is formatted at UTC too: without this the reader's own zone can name the
      // heading a day off the entries it holds.
      new Intl.DateTimeFormat(locale, {
        weekday: 'short',
        day: 'numeric',
        month: 'short',
        year: 'numeric',
        timeZone: 'UTC',
      }).format(new Date(`${day.day}T00:00:00Z`));

  return (
    <Accordion
      type="single"
      collapsible
      className="rounded-xl border border-border bg-card shadow-sm"
    >
      <AccordionItem value={day.day}>
        {/* Exactly two children, so every day's header lines up: the day on the left, the money on the
            right, flush with the amounts in the panel below. A flat list of spans lets `justify-between`
            space them differently per day. */}
        <AccordionTrigger className="gap-3 px-4 sm:px-5">
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1">
            <span className="font-semibold">{dayLabel}</span>
            <span className="text-xs font-normal text-muted-foreground">
              {t('listing.entryCount', { count: day.entries.length })}
            </span>
            {day.awaiting > 0 && (
              <Badge>{t('listing.awaitingCount', { count: day.awaiting })}</Badge>
            )}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-0.5">
            {day.totals.map((total, index) => (
              <span key={index} className="whitespace-nowrap font-semibold tabular-nums">
                {formatMoney(total)}
              </span>
            ))}
          </div>
        </AccordionTrigger>
        <AccordionContent className="px-4 pb-3 sm:px-5">
          <ul className="flex flex-col divide-y divide-border/70 border-t border-border/70">
            {day.entries.map((entry) => {
              const categoryName = categoryNames.get(entry.categoryId);
              const secondary = [entry.merchant, categoryName]
                .filter((part): part is string => Boolean(part))
                .join(' · ');
              return (
                <li
                  key={`${entry.status}-${entry.id}`}
                  aria-label={entry.description}
                  className="flex items-center justify-between gap-4 py-2.5"
                >
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate font-medium">{entry.description}</span>
                    {secondary && (
                      <span className="truncate text-xs text-muted-foreground">{secondary}</span>
                    )}
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    {entry.status === 'PENDING' && <Badge>{t('listing.statusPending')}</Badge>}
                    <span className="whitespace-nowrap text-right tabular-nums">
                      {formatMoney(entry.money)}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

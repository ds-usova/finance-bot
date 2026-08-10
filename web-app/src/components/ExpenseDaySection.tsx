import { useTranslation } from 'react-i18next';
import type { RenderedMoney } from '../api/expenses';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from './ui/accordion';
import { Badge } from './ui/badge';
import { Checkbox } from './ui/checkbox';
import {
  pendingIdsOf,
  relativeDay,
  type ExpenseDay,
  type ExpenseTickingProps,
} from './expenseDays';

export type ExpenseDaySectionProps = ExpenseTickingProps & {
  day: ExpenseDay;
  categoryNames: Map<number, string>;
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
  tickHeadroom,
}: ExpenseDaySectionProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';

  const pendingIds = pendingIdsOf(day);
  const tickedCount = pendingIds.filter((id) => tickedIds.has(id)).length;
  const untickedCount = pendingIds.length - tickedCount;
  const allTicked = pendingIds.length > 0 && tickedCount === pendingIds.length;
  const dayChecked: boolean | 'indeterminate' = allTicked
    ? true
    : tickedCount > 0
      ? 'indeterminate'
      : false;
  const dayDisabled = untickedCount > tickHeadroom;

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
        <AccordionTrigger
          className="gap-3 pr-4 pl-3 sm:pr-5"
          leading={
            // The same gutter every entry row below reserves, so the day's tick sits over the column its
            // entries' ticks stand in, and neither is flush with the card's edge.
            <span className="flex w-4 shrink-0 items-center justify-center pl-4 sm:pl-5">
              {pendingIds.length > 0 && (
                <Checkbox
                  aria-label={t('listing.dayCheckboxLabel', { count: pendingIds.length })}
                  checked={dayChecked}
                  disabled={dayDisabled}
                  onCheckedChange={() => onTickDay(pendingIds, !allTicked)}
                />
              )}
            </span>
          }
        >
          <div className="flex min-w-0 flex-1 flex-wrap items-center gap-x-3 gap-y-1">
            <span className="font-semibold">{dayLabel}</span>
            <span className="text-xs font-normal text-muted-foreground">
              {t('listing.entryCount', { count: day.entries.length })}
            </span>
            {/* One badge, not two beside each other: it names what is ticked once anything is, and what
                awaits a decision otherwise. */}
            {tickedCount > 0 ? (
              <Badge>{t('listing.tickedCount', { count: tickedCount })}</Badge>
            ) : (
              day.awaiting > 0 && (
                <Badge>{t('listing.awaitingCount', { count: day.awaiting })}</Badge>
              )
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
                  className="flex items-center gap-3 py-2.5"
                >
                  {/* Every row reserves the gutter and only a PENDING one puts a checkbox in it, so a
                      day mixing the two statuses keeps its descriptions in one column. */}
                  <span className="flex w-4 shrink-0 items-center justify-center">
                    {entry.status === 'PENDING' && (
                      <Checkbox
                        aria-label={t('listing.entryCheckboxLabel')}
                        checked={tickedIds.has(entry.id)}
                        disabled={tickHeadroom === 0 && !tickedIds.has(entry.id)}
                        onCheckedChange={(checked) => onTick(entry.id, checked === true)}
                      />
                    )}
                  </span>
                  <div className="flex min-w-0 flex-1 flex-col">
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

import { useTranslation } from 'react-i18next';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from './ui/accordion';
import { Badge } from './ui/badge';
import { relativeDay } from './expenseDays';
import type { ExpenseDay } from './expenseDays';

export type ExpenseDaySectionProps = {
  day: ExpenseDay;
  categoryNames: Map<number, string>;
};

function formatAmount(locale: string, currency: string, minorUnits: number): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(minorUnits / 100);
}

export function ExpenseDaySection({ day, categoryNames }: ExpenseDaySectionProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';

  const relative = relativeDay(day.day, new Date());
  const dayLabel =
    relative === 'today'
      ? t('listing.today')
      : relative === 'yesterday'
        ? t('listing.yesterday')
        : new Intl.DateTimeFormat(locale).format(new Date(`${day.day}T00:00:00Z`));

  return (
    <Accordion
      type="single"
      collapsible
      defaultValue={day.day}
      className="rounded-md border border-border"
    >
      <AccordionItem value={day.day}>
        <AccordionTrigger className="px-4">
          <span className="font-semibold">{dayLabel}</span>
          <span className="text-foreground/60">
            {t('listing.entryCount', { count: day.entries.length })}
          </span>
          {day.totals.map((total) => (
            <span key={total.currency} className="font-medium">
              {formatAmount(locale, total.currency, total.minorUnits)}
            </span>
          ))}
          {day.awaiting > 0 && (
            <span className="text-foreground/60">
              {t('listing.awaitingCount', { count: day.awaiting })}
            </span>
          )}
        </AccordionTrigger>
        <AccordionContent className="px-4">
          <ul className="flex flex-col gap-2">
            {day.entries.map((entry) => {
              const categoryName = categoryNames.get(entry.categoryId);
              return (
                <li
                  key={`${entry.status}-${entry.id}`}
                  aria-label={entry.description}
                  className="flex items-center justify-between gap-2"
                >
                  <div className="flex flex-col">
                    <span>{entry.description}</span>
                    {entry.merchant && <span className="text-foreground/60">{entry.merchant}</span>}
                    {categoryName && <span className="text-foreground/60">{categoryName}</span>}
                  </div>
                  <div className="flex items-center gap-2">
                    <span>{formatAmount(locale, entry.currency, entry.amountMinorUnits)}</span>
                    <Badge variant={entry.status === 'PENDING' ? 'pending' : 'muted'}>
                      {entry.status === 'PENDING'
                        ? t('listing.statusPending')
                        : t('listing.statusRecorded')}
                    </Badge>
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

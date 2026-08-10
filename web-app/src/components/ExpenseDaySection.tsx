import { ChevronsUpDown } from 'lucide-react';
import { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import type { Category, Expense, Grouping, RenderedMoney } from '../api/expenses';
import { CategoryPicker } from './CategoryPicker';
import { ErrorBanner } from './ErrorBanner';
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from './ui/accordion';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { Checkbox } from './ui/checkbox';
import {
  entryKey,
  pendingIdsOf,
  relativeDay,
  type ExpenseCategoryChangeProps,
  type ExpenseDay,
  type ExpenseTickingProps,
} from './expenseDays';

export type ExpenseDaySectionProps = ExpenseTickingProps &
  ExpenseCategoryChangeProps & {
    day: ExpenseDay;
    categoryNames: Map<number, string>;
  };

function formatMoney(money: RenderedMoney): string {
  return `${money.currency}${money.separator}${money.amount}`;
}

type CategoryControlProps = {
  entry: Expense;
  categoryName: string;
  categories: Category[];
  groupings: Grouping[];
  changingKey: string | null;
  onChangeCategory: (entry: Expense, categoryId: number) => void;
};

/** The row's category, offered as a ghost trigger with a chevron. Busy while its own change is out, disabled
 * while any other row's is, since one change out disables the rest of the listing rather than one row of it. */
function CategoryControl({
  entry,
  categoryName,
  categories,
  groupings,
  changingKey,
  onChangeCategory,
}: CategoryControlProps) {
  const { t } = useTranslation();
  const busy = changingKey === entryKey(entry);
  const disabled = changingKey !== null && !busy;

  return (
    <CategoryPicker
      groupings={groupings}
      categories={categories}
      categoryId={entry.categoryId}
      onChange={(categoryId) => {
        if (categoryId !== undefined) {
          onChangeCategory(entry, categoryId);
        }
      }}
      withAll={false}
      width="own"
      searchPlaceholder={t('listing.searchCategories')}
      emptyText={t('listing.noCategory')}
      trigger={
        <Button
          variant="ghost"
          aria-label={t('listing.changeCategoryLabel', { description: entry.description })}
          aria-busy={busy}
          disabled={disabled}
          // Pulled left by its own horizontal padding, so the name starts on the column's edge like the cells
          // above and below it, while the hover and focus surface keeps its padding.
          className="-ml-1.5 h-auto min-w-0 max-w-full gap-1 px-1.5 py-0.5 font-normal text-muted-foreground"
        >
          <span className="truncate">{categoryName}</span>
          <ChevronsUpDown aria-hidden="true" className="h-3 w-3 shrink-0 opacity-50" />
        </Button>
      }
    />
  );
}

export function ExpenseDaySection({
  day,
  categoryNames,
  categories,
  groupings,
  tickedIds,
  onTick,
  onTickDay,
  tickHeadroom,
  changingKey,
  changeFailure,
  focusDay,
  onChangeCategory,
}: ExpenseDaySectionProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const headerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (focusDay === day.day) {
      headerRef.current?.focus();
    }
  }, [focusDay, day.day]);

  const pendingIds = pendingIdsOf(day);
  // A day with nothing pending holds no tick anywhere — not in its header and not in any of its rows — so it
  // reserves no column for one either, and its whole card sits flush with the card's own padding.
  const hasPending = pendingIds.length > 0;
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
          ref={headerRef}
          // With a tick beside it the left padding stands the chevron off it; without one the trigger carries
          // the panel's own side padding itself, so the chevron starts where the descriptions below it do.
          className={`gap-3 pr-4 sm:pr-5 ${hasPending ? 'pl-3' : 'pl-4 sm:pl-5'}`}
          leading={
            hasPending && (
              // The same gutter every entry row below reserves, so the day's tick sits over the column its
              // entries' ticks stand in. The width has to cover the panel's own side padding *and* the tick
              // column inside it — `w-4 pl-4` is a 16px box entirely filled by its padding, which centres the
              // tick half a column to the left of every tick beneath it.
              <span className="flex w-8 shrink-0 items-center justify-center pl-4 sm:w-9 sm:pl-5">
                <Checkbox
                  aria-label={t('listing.dayCheckboxLabel', { count: pendingIds.length })}
                  checked={dayChecked}
                  disabled={dayDisabled}
                  onCheckedChange={() => onTickDay(pendingIds, !allTicked)}
                />
              </span>
            )
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
              const key = entryKey(entry);
              const categoryName = categoryNames.get(entry.categoryId);
              const failureMessage = changeFailure?.key === key ? changeFailure.message : null;
              return (
                <li
                  key={key}
                  aria-label={entry.description}
                  className="flex flex-col gap-1.5 py-2.5"
                >
                  {/* A grid rather than a flex row, because these are columns and have to line up down the
                      whole day. Under flex the two trailing cells are sized by their own content — a PENDING
                      row carries a badge where a RECORDED one carries nothing — so every row left a different
                      amount of space for the three middle cells to divide, and no two rows agreed on where a
                      column started. Fixed tracks for the badge and the figure make the space they leave the
                      same on every row. Every cell is rendered whether or not it has content, for the same
                      reason the gutter is. The category gets a wider share than the merchant despite holding
                      shorter text, because it is a control: its chevron and its padding eat width that plain
                      text does not. */}
                  <div
                    className={`grid items-center gap-3 ${
                      hasPending
                        ? 'grid-cols-[1rem_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.25fr)_4.5rem_7rem]'
                        : 'grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.25fr)_4.5rem_7rem]'
                    }`}
                  >
                    {/* The column stands on every row of a day that has anything pending, so a day mixing the
                        two statuses keeps its descriptions in one line; only a PENDING row fills it. */}
                    {hasPending && (
                      <span className="flex items-center justify-center">
                        {entry.status === 'PENDING' && (
                          <Checkbox
                            aria-label={t('listing.entryCheckboxLabel')}
                            checked={tickedIds.has(entry.id)}
                            disabled={tickHeadroom === 0 && !tickedIds.has(entry.id)}
                            onCheckedChange={(checked) => onTick(entry.id, checked === true)}
                          />
                        )}
                      </span>
                    )}
                    <span className="truncate font-medium">{entry.description}</span>
                    <span className="truncate text-xs text-muted-foreground">{entry.merchant}</span>
                    <span className="flex min-w-0 items-center">
                      {categoryName && (
                        <CategoryControl
                          entry={entry}
                          categoryName={categoryName}
                          categories={categories}
                          groupings={groupings}
                          changingKey={changingKey}
                          onChangeCategory={onChangeCategory}
                        />
                      )}
                    </span>
                    <span className="flex justify-end">
                      {entry.status === 'PENDING' && <Badge>{t('listing.statusPending')}</Badge>}
                    </span>
                    <span className="truncate text-right tabular-nums">
                      {formatMoney(entry.money)}
                    </span>
                  </div>
                  {failureMessage && <ErrorBanner message={failureMessage} />}
                </li>
              );
            })}
          </ul>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}

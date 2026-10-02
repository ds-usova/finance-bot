import { ChevronsUpDown } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
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
    /** Whether an acceptance just posted entries on this day, which lands the stamp on its header. */
    posted?: boolean;
    /** The day opens when this becomes `true`, and the person can still close it. */
    opened?: boolean;
  };

function formatMoney(money: RenderedMoney): string {
  return `${money.currency}${money.separator}${money.amount}`;
}

// Where each cell of an entry line sits. On a phone a line prints on two rows — description and amount, then
// merchant, category and the pending mark — and from `sm` up it prints on one, in the order the cells come.
// A day with anything pending reserves the margin column for the ticks on every row, so a day mixing the two
// statuses keeps its descriptions in one column.
const LINE_WITH_MARGIN = {
  grid: 'grid-cols-[1.5rem_minmax(0,1fr)_minmax(0,1fr)_auto] sm:grid-cols-[1.5rem_minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.25fr)_5.5rem_7.5rem]',
  tick: 'col-start-1 row-span-2 row-start-1 sm:row-span-1',
  description: 'col-span-2 col-start-2 row-start-1 sm:col-span-1',
  merchant: 'col-start-2 row-start-2 sm:col-start-3 sm:row-start-1',
  category: 'col-start-3 row-start-2 sm:col-start-4 sm:row-start-1',
  mark: 'col-start-4 row-start-2 sm:col-start-5 sm:row-start-1',
  amount: 'col-start-4 row-start-1 sm:col-start-6',
};

const LINE_WITHOUT_MARGIN = {
  grid: 'grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto] sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)_minmax(0,1.25fr)_5.5rem_7.5rem]',
  tick: '',
  description: 'col-span-2 col-start-1 row-start-1 sm:col-span-1',
  merchant: 'col-start-1 row-start-2 sm:col-start-2 sm:row-start-1',
  category: 'col-start-2 row-start-2 sm:col-start-3 sm:row-start-1',
  mark: 'col-start-3 row-start-2 sm:col-start-4 sm:row-start-1',
  amount: 'col-start-3 row-start-1 sm:col-start-5',
};

type CategoryControlProps = {
  entry: Expense;
  categoryName: string;
  categories: Category[];
  groupings: Grouping[];
  changingKey: string | null;
  onChangeCategory: (entry: Expense, categoryId: number) => void;
};

/** The row's category, offered as a quiet trigger with a chevron. Busy while its own change is out, disabled
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
          // above and below it, while the hover and focus surface keeps its padding. The dotted underline is
          // the passbook's mark for a field that can be written over.
          className="relative -ml-1.5 h-8 max-w-full min-w-0 gap-1 px-1.5 before:absolute before:inset-x-0 before:-inset-y-1.5 before:content-[''] text-[0.8125rem] font-normal text-pencil underline decoration-rule-strong decoration-dotted underline-offset-[5px] hover:text-ink aria-busy:animate-pulse"
        >
          <span className="truncate">{categoryName}</span>
          <ChevronsUpDown aria-hidden="true" className="h-3 w-3 shrink-0 opacity-70" />
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
  posted = false,
  opened = false,
}: ExpenseDaySectionProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.resolvedLanguage ?? 'en';
  const headerRef = useRef<HTMLButtonElement>(null);
  const [openValue, setOpenValue] = useState(opened ? day.day : '');

  useEffect(() => {
    if (opened) {
      setOpenValue(day.day);
    }
  }, [opened, day.day]);

  useEffect(() => {
    if (focusDay === day.day) {
      headerRef.current?.focus();
    }
  }, [focusDay, day.day]);

  const pendingIds = pendingIdsOf(day);
  // A day with nothing pending holds no tick anywhere — not in its header and not in any of its rows — so it
  // reserves no margin column for one either.
  const hasPending = pendingIds.length > 0;
  const line = hasPending ? LINE_WITH_MARGIN : LINE_WITHOUT_MARGIN;
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
      value={openValue}
      onValueChange={setOpenValue}
      className="relative border-b border-rule"
    >
      <AccordionItem value={day.day}>
        {/* Exactly two children, so every day's header lines up: the date on the left, the money on the
            right, flush with the amounts on the lines below. */}
        <AccordionTrigger
          ref={headerRef}
          className={`gap-2.5 pr-0 ${hasPending ? 'pl-0' : 'pl-0.5'}`}
          leading={
            hasPending && (
              // The same margin column every entry line below reserves, so the day's tick sits over its
              // entries' ticks.
              <span className="flex w-6 shrink-0 items-center justify-start">
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
          <div className="flex min-w-0 flex-1 flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="text-[0.9375rem] font-semibold">{dayLabel}</span>
            <span className="text-xs text-pencil">
              {t('listing.entryCount', { count: day.entries.length })}
            </span>
            {/* One mark, not two beside each other: it names what is ticked once anything is, and what
                awaits a decision otherwise. */}
            {tickedCount > 0 ? (
              <Badge tone="stamp">{t('listing.tickedCount', { count: tickedCount })}</Badge>
            ) : (
              day.awaiting > 0 && (
                <Badge>{t('listing.awaitingCount', { count: day.awaiting })}</Badge>
              )
            )}
          </div>
          <div className="flex shrink-0 flex-col items-end gap-0.5 self-center">
            {day.totals.map((total, index) => (
              <span
                key={index}
                className="font-mono text-[0.9375rem] font-bold whitespace-nowrap tabular-nums"
              >
                {formatMoney(total)}
              </span>
            ))}
          </div>
        </AccordionTrigger>
        <AccordionContent>
          <ul className="flex flex-col border-t border-dashed border-rule-strong">
            {day.entries.map((entry) => {
              const key = entryKey(entry);
              const categoryName = categoryNames.get(entry.categoryId);
              const failureMessage = changeFailure?.key === key ? changeFailure.message : null;
              const pending = entry.status === 'PENDING';
              return (
                <li
                  key={key}
                  aria-label={entry.description}
                  className="flex flex-col gap-1.5 border-b border-rule py-2 last:border-b-0 sm:py-1.5"
                >
                  {/* A grid rather than a flex row, because these are columns and have to line up down the
                      whole day. Every cell is rendered whether or not it has content, for the same reason the
                      margin is. */}
                  <div
                    className={`grid items-center gap-x-3 gap-y-0.5 ${line.grid} ${pending ? 'text-pencil' : ''}`}
                  >
                    {hasPending && (
                      <span className={`flex items-center ${line.tick}`}>
                        {pending && (
                          <Checkbox
                            aria-label={t('listing.entryCheckboxLabel')}
                            checked={tickedIds.has(entry.id)}
                            disabled={tickHeadroom === 0 && !tickedIds.has(entry.id)}
                            onCheckedChange={(checked) => onTick(entry.id, checked === true)}
                          />
                        )}
                      </span>
                    )}
                    <span
                      className={`truncate font-medium ${pending ? '' : 'text-ink'} ${line.description}`}
                    >
                      {entry.description}
                    </span>
                    <span className={`truncate text-[0.8125rem] text-pencil ${line.merchant}`}>
                      {entry.merchant}
                    </span>
                    <span className={`flex min-w-0 items-center ${line.category}`}>
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
                    <span className={`flex justify-end ${line.mark}`}>
                      {pending && <Badge>{t('listing.statusPending')}</Badge>}
                    </span>
                    <span
                      className={`truncate text-right font-mono tabular-nums ${pending ? 'italic' : 'text-ink'} ${line.amount}`}
                    >
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
      {posted && (
        // The teller's stamp, struck across the day an acceptance just posted. Decorative: the acceptance
        // already speaks through the listing it reads back.
        <span
          aria-hidden="true"
          // Struck across the rule above the day, clear of its date and its figures, the way a stamp
          // overlaps the edge of the line it posts.
          className="pointer-events-none absolute top-0 right-[38%] z-20 animate-stamp-land rounded-sm border-2 border-stamp px-2.5 py-0.5 text-sm font-bold whitespace-nowrap text-stamp outline outline-1 outline-offset-2 outline-stamp/60 mix-blend-multiply dark:mix-blend-screen"
        >
          {t('filters.statusRecorded')}
        </span>
      )}
    </Accordion>
  );
}

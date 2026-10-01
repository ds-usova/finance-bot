import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Alert } from './ui/alert';
import { ExpenseDaySection } from './ExpenseDaySection';
import {
  toDaySections,
  type ExpenseCategoryChangeProps,
  type ExpenseTickingProps,
} from './expenseDays';

export type ExpenseListProps = ExpenseTickingProps &
  ExpenseCategoryChangeProps & {
    page: ExpensePage;
    /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
    categoryNames: Map<number, string>;
    /** The days an acceptance just posted entries on. */
    postedDays?: ReadonlySet<string>;
  };

export function ExpenseList({
  page,
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
  postedDays,
}: ExpenseListProps) {
  const { t } = useTranslation();

  if (page.items.length === 0) {
    return (
      // A blank page: ruled lines with nothing printed on them yet.
      <Alert className="min-h-40 items-center justify-center border-y-0 bg-[repeating-linear-gradient(to_bottom,transparent_0,transparent_calc(2.5rem-1px),var(--rule)_calc(2.5rem-1px),var(--rule)_2.5rem)] text-pencil">
        {/* On a patch of the page's own paper, so the rules behind it never run through the words. */}
        <span className="bg-paper px-4 py-2">{t('listing.empty')}</span>
      </Alert>
    );
  }

  return (
    <div className="flex flex-col">
      {toDaySections(page.items, page.dayTotals).map((day) => (
        <ExpenseDaySection
          key={day.day}
          day={day}
          categoryNames={categoryNames}
          categories={categories}
          groupings={groupings}
          tickedIds={tickedIds}
          onTick={onTick}
          onTickDay={onTickDay}
          tickHeadroom={tickHeadroom}
          changingKey={changingKey}
          changeFailure={changeFailure}
          focusDay={focusDay}
          onChangeCategory={onChangeCategory}
          posted={postedDays?.has(day.day) ?? false}
        />
      ))}
    </div>
  );
}

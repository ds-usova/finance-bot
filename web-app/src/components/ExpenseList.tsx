import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Alert } from './ui/alert';
import { ExpenseDaySection } from './ExpenseDaySection';
import { toDaySections, type ExpenseTickingProps } from './expenseDays';

export type ExpenseListProps = ExpenseTickingProps & {
  page: ExpensePage;
  /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
  categoryNames: Map<number, string>;
};

export function ExpenseList({
  page,
  categoryNames,
  tickedIds,
  onTick,
  onTickDay,
  tickHeadroom,
}: ExpenseListProps) {
  const { t } = useTranslation();

  if (page.items.length === 0) {
    return <Alert>{t('listing.empty')}</Alert>;
  }

  return (
    <div className="flex flex-col gap-4">
      {toDaySections(page.items, page.dayTotals).map((day) => (
        <ExpenseDaySection
          key={day.day}
          day={day}
          categoryNames={categoryNames}
          tickedIds={tickedIds}
          onTick={onTick}
          onTickDay={onTickDay}
          tickHeadroom={tickHeadroom}
        />
      ))}
    </div>
  );
}

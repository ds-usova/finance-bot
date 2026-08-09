import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Alert } from './ui/alert';
import { ExpenseDaySection } from './ExpenseDaySection';
import { toDaySections } from './expenseDays';

export type ExpenseListProps = {
  page: ExpensePage;
  /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
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

export function ExpenseList({
  page,
  categoryNames,
  tickedIds,
  onTick,
  onTickDay,
  atBound,
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
          atBound={atBound}
        />
      ))}
    </div>
  );
}

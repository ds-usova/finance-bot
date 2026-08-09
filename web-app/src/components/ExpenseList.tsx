import { useTranslation } from 'react-i18next';
import type { ExpensePage } from '../api/expenses';
import { Alert } from './ui/alert';
import { ExpenseDaySection } from './ExpenseDaySection';
import { toDaySections } from './expenseDays';

export type ExpenseListProps = {
  page: ExpensePage;
  /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
  categoryNames: Map<number, string>;
};

export function ExpenseList({ page, categoryNames }: ExpenseListProps) {
  const { t } = useTranslation();

  if (page.items.length === 0) {
    return <Alert>{t('listing.empty')}</Alert>;
  }

  return (
    <div className="flex flex-col gap-4">
      {toDaySections(page.items).map((day) => (
        <ExpenseDaySection key={day.day} day={day} categoryNames={categoryNames} />
      ))}
    </div>
  );
}

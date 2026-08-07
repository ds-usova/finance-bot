import type { FC } from 'react';
import type { ExpensePage } from '../api/expenses';

export type ExpenseListProps = {
  page: ExpensePage;
  /** Each category's name by its id. A row whose category is missing renders unnamed rather than failing. */
  categoryNames: Map<number, string>;
};

/**
 * TODO RU05/GU05: renders one row per entry, in the order the page gives them, each showing its description,
 * amount, currency, status, its category's name and its merchant when it has one; says there is nothing to show
 * for an empty page; and says how many of the total are shown when the total exceeds the items.
 */
export const ExpenseList: FC<ExpenseListProps> = () => null;

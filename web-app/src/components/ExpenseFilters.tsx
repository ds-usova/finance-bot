import type { FC } from 'react';
import type { Category, ExpenseFilter, Grouping } from '../api/expenses';

export type ExpenseFiltersProps = {
  groupings: Grouping[];
  categories: Category[];
  filter: ExpenseFilter;
  onChange: (filter: ExpenseFilter) => void;
};

/**
 * TODO RU06/GU06: offers every grouping, every category, a status and a from/to period, each findable by its
 * accessible name, and calls onChange with the next filter. Choosing a grouping narrows the categories it offers
 * to that grouping's, client-side from the tree it already holds; clearing it offers them all again and drops a
 * selection that fell outside. The period's label says the days narrow when a row was recorded.
 */
export const ExpenseFilters: FC<ExpenseFiltersProps> = () => null;

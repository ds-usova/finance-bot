import type { ExpenseDay } from './expenseDays';

export type ExpenseDaySectionProps = {
  day: ExpenseDay;
  categoryNames: Map<number, string>;
};

// Renders one day's entries, expandable, with its header naming the day, its entry count, its awaiting count
// and its per-currency totals. Left unrendered until GU04, so ExpenseList has something to compose against.
export function ExpenseDaySection(props: ExpenseDaySectionProps) {
  void props;
  return null;
}

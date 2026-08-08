import type { Expense } from '../api/expenses';

export type DayTotal = {
  currency: string;
  minorUnits: number;
};

export type ExpenseDay = {
  day: string;
  entries: Expense[];
  awaiting: number;
  totals: DayTotal[];
};

// Cuts the page into UTC days, newest first, each carrying its own entries in the page's order, its awaiting
// count, and its totals — one per currency, summed over that day's RECORDED entries only.
export function toDaySections(items: Expense[]): ExpenseDay[] {
  throw new Error(`not implemented: ${items.length}`);
}

// Names a day today or yesterday against the UTC day of now, or null for anything older.
export function relativeDay(day: string, now: Date): 'today' | 'yesterday' | null {
  throw new Error(`not implemented: ${day} ${now.toISOString()}`);
}

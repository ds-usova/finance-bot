import type { DayTotal, Expense, RenderedMoney } from '../api/expenses';

export type ExpenseDay = {
  day: string;
  entries: Expense[];
  awaiting: number;
  totals: RenderedMoney[];
};

function utcDayString(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// Cuts the page into UTC days, newest first, each carrying its own entries in the page's order and its awaiting
// count and the figures dayTotals answers for its day.
export function toDaySections(items: Expense[], dayTotals: DayTotal[]): ExpenseDay[] {
  const totalsByDay = new Map(dayTotals.map((dayTotal) => [dayTotal.day, dayTotal.amounts]));
  const sections = new Map<string, ExpenseDay>();

  for (const item of items) {
    const day = utcDayString(new Date(item.createdAt));
    let section = sections.get(day);
    if (!section) {
      section = { day, entries: [], awaiting: 0, totals: totalsByDay.get(day) ?? [] };
      sections.set(day, section);
    }
    section.entries.push(item);
    if (item.status !== 'RECORDED') {
      section.awaiting += 1;
    }
  }

  return Array.from(sections.values()).sort((a, b) => b.day.localeCompare(a.day));
}

// Names a day today or yesterday against the UTC day of now, or null for anything older.
export function relativeDay(day: string, now: Date): 'today' | 'yesterday' | null {
  const today = utcDayString(now);
  if (day === today) {
    return 'today';
  }

  const [year, month, date] = day.split('-').map(Number);
  const dayMs = Date.UTC(year!, month! - 1, date);
  const todayMs = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
  const diffDays = Math.round((todayMs - dayMs) / 86_400_000);

  return diffDays === 1 ? 'yesterday' : null;
}

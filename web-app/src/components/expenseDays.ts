import type { DayTotal, Expense, ExpensePage, RenderedMoney } from '../api/expenses';

export type ExpenseDay = {
  day: string;
  entries: Expense[];
  awaiting: number;
  totals: RenderedMoney[];
};

/** What a component rendering pending entries takes to show the ticks and report a change to them. */
export type ExpenseTickingProps = {
  /** The ids of ticked `PENDING` entries, owned by the page. */
  tickedIds: ReadonlySet<number>;
  /** One entry ticked or unticked. */
  onTick: (id: number, ticked: boolean) => void;
  /** A whole day ticked or unticked. */
  onTickDay: (ids: number[], ticked: boolean) => void;
  /** How many more entries may be ticked before the endpoint's bound on one request. */
  tickHeadroom: number;
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

// The ids of a day's PENDING entries, which is what a "select all" tick on that day can carry in one call.
export function pendingIdsOf(day: ExpenseDay): number[] {
  return day.entries.filter((entry) => entry.status === 'PENDING').map((entry) => entry.id);
}

// The UTC days a set of ticked ids sits on, read against the page's own entries rather than the reader's
// zone. An id naming no PENDING entry on the page contributes no day, and an id a RECORDED entry shares with
// a PENDING one on another day answers only the PENDING entry's day — an id is unique within a status, not
// across the two.
export function touchedDaysOf(page: ExpensePage, ids: number[]): Set<string> {
  const idSet = new Set(ids);
  const days = new Set<string>();

  for (const item of page.items) {
    if (item.status === 'PENDING' && idSet.has(item.id)) {
      days.add(utcDayString(new Date(item.createdAt)));
    }
  }

  return days;
}

// Merges a freshly read page back into the page on screen, replacing only the entries and the dayTotals
// figure of the day the fresh read answered — the other days stand exactly as they were, and `limit`,
// `offset` and `total` stay the original page's. A day the fresh read answers nothing for is dropped
// rather than left showing entries that moved.
export function mergeDay(page: ExpensePage, day: string, fresh: ExpensePage): ExpensePage {
  const otherItems = page.items.filter((item) => utcDayString(new Date(item.createdAt)) !== day);
  const freshItems = fresh.items.filter((item) => utcDayString(new Date(item.createdAt)) === day);
  const otherTotals = page.dayTotals.filter((dayTotal) => dayTotal.day !== day);
  const freshTotal = fresh.dayTotals.find((dayTotal) => dayTotal.day === day);

  return {
    ...page,
    items: [...otherItems, ...freshItems],
    dayTotals: freshTotal ? [...otherTotals, freshTotal] : otherTotals,
  };
}

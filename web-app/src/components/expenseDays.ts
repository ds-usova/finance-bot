import type {
  Category,
  DayTotal,
  Expense,
  ExpensePage,
  Grouping,
  RenderedMoney,
} from '../api/expenses';

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

/** What a component offering a row's category to be changed takes to render the control, its picker and its
 * busy, disabled and refused states. */
export type ExpenseCategoryChangeProps = {
  /** What the picker offers and how it is grouped; empty where the read failed. */
  categories: Category[];
  groupings: Grouping[];
  /** The `entryKey` of the row whose change is out, or nothing. */
  changingKey: string | null;
  /** The surface's own refusal wording, and the row it was refused for. */
  changeFailure: { key: string; message: string } | null;
  /** The day whose header takes focus, set when a read back removed the row a person's control was on. */
  focusDay: string | null;
  /** A row refiled to a category the person picked. */
  onChangeCategory: (entry: Expense, categoryId: number) => void;
};

// One row's identity, both as a React key and as the row a change or a refusal names: an id is unique within
// a status, not across the two, so a recorded entry and a proposal sharing an id stay apart.
export function entryKey(entry: Pick<Expense, 'status' | 'id'>): string {
  return `${entry.status}-${entry.id}`;
}

function utcDayString(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, '0');
  const day = String(date.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** The UTC day an entry's `createdAt` sits on, the same one `toDaySections` cut the page by. */
export function utcDayOf(createdAt: string): string {
  return utcDayString(new Date(createdAt));
}

// Cuts the page into UTC days, newest first, each carrying its own entries in the page's order and its awaiting
// count and the figures dayTotals answers for its day.
export function toDaySections(items: Expense[], dayTotals: DayTotal[]): ExpenseDay[] {
  const totalsByDay = new Map(dayTotals.map((dayTotal) => [dayTotal.day, dayTotal.amounts]));
  const sections = new Map<string, ExpenseDay>();

  for (const item of items) {
    const day = utcDayOf(item.createdAt);
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
      days.add(utcDayOf(item.createdAt));
    }
  }

  return days;
}

// Merges a freshly read page back into the page on screen, replacing only the entries and the dayTotals
// figure of the day the fresh read answered — the other days stand exactly as they were, and `limit`,
// `offset` and `total` stay the original page's. A day the fresh read answers nothing for is dropped
// rather than left showing entries that moved.
export function mergeDay(page: ExpensePage, day: string, fresh: ExpensePage): ExpensePage {
  const otherItems = page.items.filter((item) => utcDayOf(item.createdAt) !== day);
  const freshItems = fresh.items.filter((item) => utcDayOf(item.createdAt) === day);
  const otherTotals = page.dayTotals.filter((dayTotal) => dayTotal.day !== day);
  const freshTotal = fresh.dayTotals.find((dayTotal) => dayTotal.day === day);

  return {
    ...page,
    items: [...otherItems, ...freshItems],
    dayTotals: freshTotal ? [...otherTotals, freshTotal] : otherTotals,
  };
}

// Replaces the page's own entry sharing the answered one's status and id, leaving every other entry, its own
// position in `items`, and `limit`, `offset`, `total` and `dayTotals` exactly as the original page held them.
// An entry the page no longer holds — its id under that status left the page some other way — answers the
// page unchanged rather than throwing.
export function replaceEntry(page: ExpensePage, entry: Expense): ExpensePage {
  const key = entryKey(entry);
  return {
    ...page,
    items: page.items.map((item) => (entryKey(item) === key ? entry : item)),
  };
}

// Keeps only the ids naming a PENDING entry the page still holds, since a tick names a pending entry and a
// refile can carry one off the page the ticks were read against.
export function ticksStillOnPage(page: ExpensePage, tickedIds: ReadonlySet<number>): Set<number> {
  const pendingIds = new Set(
    page.items.filter((item) => item.status === 'PENDING').map((item) => item.id),
  );
  return new Set([...tickedIds].filter((id) => pendingIds.has(id)));
}

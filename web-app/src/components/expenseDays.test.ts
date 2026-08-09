import { describe, expect, it, vi } from 'vitest';
import type { DayTotal, ExpensePage } from '../api/expenses';
import { anExpense, anExpensePage } from '../testing/fixtures';
import { mergeDay, pendingIdsOf, relativeDay, toDaySections, touchedDaysOf } from './expenseDays';
import type { ExpenseDay } from './expenseDays';

function itemsOnDay(items: ExpensePage['items'], day: string) {
  return items.filter((item) => item.createdAt.startsWith(day));
}

describe('cutting a page into day sections', () => {
  it('cuts entries into one section per UTC day, keeping the sections and each day’s entries in the page’s order', () => {
    const newestA = anExpense({
      id: 1,
      status: 'RECORDED',
      createdAt: '2026-08-03T09:00:00Z',
      description: 'a',
    });
    const newestB = anExpense({
      id: 2,
      status: 'RECORDED',
      createdAt: '2026-08-03T08:00:00Z',
      description: 'b',
    });
    const middle = anExpense({
      id: 3,
      status: 'RECORDED',
      createdAt: '2026-08-02T10:00:00Z',
      description: 'c',
    });
    const oldest = anExpense({
      id: 4,
      status: 'RECORDED',
      createdAt: '2026-08-01T10:00:00Z',
      description: 'd',
    });

    const sections = toDaySections([newestA, newestB, middle, oldest], []);

    expect(sections.map((section) => section.day)).toEqual([
      '2026-08-03',
      '2026-08-02',
      '2026-08-01',
    ]);
    expect(sections[0]?.entries).toEqual([newestA, newestB]);
    expect(sections[1]?.entries).toEqual([middle]);
    expect(sections[2]?.entries).toEqual([oldest]);
  });

  it('groups by the UTC day of createdAt, not the local day the reader is already in', () => {
    // UTC+14: local calendar has already turned over to the 2nd while it's still 23:30 UTC on the 1st.
    vi.stubEnv('TZ', 'Pacific/Kiritimati');
    try {
      const entry = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T23:30:00Z' });

      const sections = toDaySections([entry], []);

      expect(sections).toHaveLength(1);
      expect(sections[0]?.day).toBe('2026-08-01');
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('carries the one figure its day was answered, whatever the entry count', () => {
    const first = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const second = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const third = anExpense({ id: 3, status: 'RECORDED', createdAt: '2026-08-01T10:00:00Z' });
    const dayTotal: DayTotal = {
      day: '2026-08-01',
      amounts: [{ amount: '6.00', currency: 'EUR', separator: '' }],
    };

    const sections = toDaySections([first, second, third], [dayTotal]);

    expect(sections[0]?.totals).toEqual([{ amount: '6.00', currency: 'EUR', separator: '' }]);
  });

  it('keeps one total per currency, converting neither into the other', () => {
    const eur = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const usd = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const dayTotal: DayTotal = {
      day: '2026-08-01',
      amounts: [
        { amount: '1.00', currency: 'EUR', separator: '' },
        { amount: '2.00', currency: 'USD', separator: '' },
      ],
    };

    const sections = toDaySections([eur, usd], [dayTotal]);

    expect(sections[0]?.totals).toEqual(dayTotal.amounts);
  });

  it('carries the figure its day was answered while counting every entry and every one awaiting a decision', () => {
    const recordedA = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const recordedB = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const pending = anExpense({ id: 3, status: 'PENDING', createdAt: '2026-08-01T10:00:00Z' });
    const dayTotal: DayTotal = {
      day: '2026-08-01',
      amounts: [{ amount: '3.00', currency: 'EUR', separator: '' }],
    };

    const sections = toDaySections([recordedA, recordedB, pending], [dayTotal]);

    expect(sections[0]?.totals).toEqual([{ amount: '3.00', currency: 'EUR', separator: '' }]);
    expect(sections[0]?.entries).toHaveLength(3);
    expect(sections[0]?.awaiting).toBe(1);
  });

  it('gives each section the figures dayTotals answers for its own day', () => {
    const dayOne = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const dayTwo = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-02T08:00:00Z' });
    const totalOne: DayTotal = {
      day: '2026-08-01',
      amounts: [{ amount: '12.50', currency: '€', separator: '' }],
    };
    const totalTwo: DayTotal = {
      day: '2026-08-02',
      amounts: [{ amount: '900', currency: '¥', separator: '' }],
    };

    const sections = toDaySections([dayOne, dayTwo], [totalOne, totalTwo]);

    expect(sections.find((section) => section.day === '2026-08-01')?.totals).toEqual(
      totalOne.amounts,
    );
    expect(sections.find((section) => section.day === '2026-08-02')?.totals).toEqual(
      totalTwo.amounts,
    );
  });

  it('leaves a section’s totals empty when dayTotals holds no element for its day, without touching its entries or awaiting count', () => {
    const recorded = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const pending = anExpense({ id: 2, status: 'PENDING', createdAt: '2026-08-01T09:00:00Z' });
    // A dayTotals element for a different day, so the empty totals below can only come from the ledger
    // answering no element for this day — not from an incidentally empty dayTotals list.
    const otherDayTotal: DayTotal = {
      day: '2026-08-02',
      amounts: [{ amount: '5.00', currency: 'EUR', separator: '' }],
    };

    const sections = toDaySections([recorded, pending], [otherDayTotal]);

    expect(sections[0]?.totals).toEqual([]);
    expect(sections[0]?.entries).toEqual([recorded, pending]);
    expect(sections[0]?.awaiting).toBe(1);
  });

  it('creates no section for a dayTotals element whose day no entry falls on', () => {
    const entry = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const orphanTotal: DayTotal = {
      day: '2026-08-09',
      amounts: [{ amount: '1.00', currency: 'EUR', separator: '' }],
    };

    const sections = toDaySections([entry], [orphanTotal]);

    expect(sections).toHaveLength(1);
    expect(sections.map((section) => section.day)).toEqual(['2026-08-01']);
  });

  it('gives every section an empty totals when dayTotals is empty', () => {
    const dayOne = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const dayTwo = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-02T08:00:00Z' });

    const sections = toDaySections([dayOne, dayTwo], []);

    expect(sections.every((section) => section.totals.length === 0)).toBe(true);
  });

  it('keeps a RECORDED entry and a PENDING proposal that share an id, since status is part of an entry’s identity', () => {
    const recorded = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const pending = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-01T09:00:00Z' });

    const sections = toDaySections([recorded, pending], []);

    expect(sections[0]?.entries).toEqual([recorded, pending]);
  });

  it('answers no sections for an empty page', () => {
    expect(toDaySections([], [])).toEqual([]);
  });
});

describe('naming a day relative to now', () => {
  it('names a day today when it equals the UTC day of now', () => {
    const now = new Date('2026-08-05T12:00:00Z');

    expect(relativeDay('2026-08-05', now)).toBe('today');
  });

  it('names a day yesterday when it is the UTC day before now', () => {
    const now = new Date('2026-08-05T12:00:00Z');

    expect(relativeDay('2026-08-04', now)).toBe('yesterday');
  });

  it('names nothing for a day three UTC days back, leaving the section headed with its date', () => {
    const now = new Date('2026-08-05T12:00:00Z');

    expect(relativeDay('2026-08-02', now)).toBeNull();
  });

  it('resolves against the UTC today even when the local day is already ahead of it', () => {
    // UTC+14: local calendar reads tomorrow already while it's still 23:30 UTC today.
    vi.stubEnv('TZ', 'Pacific/Kiritimati');
    try {
      const now = new Date('2026-08-01T23:30:00Z');

      expect(relativeDay('2026-08-01', now)).toBe('today');
    } finally {
      vi.unstubAllEnvs();
    }
  });
});

describe('pendingIdsOf', () => {
  it('answers only the pending ids when a day mixes two pending entries and one recorded one', () => {
    const pendingA = anExpense({ id: 1, status: 'PENDING' });
    const recorded = anExpense({ id: 2, status: 'RECORDED' });
    const pendingB = anExpense({ id: 3, status: 'PENDING' });
    const day: ExpenseDay = {
      day: '2026-08-01',
      entries: [pendingA, recorded, pendingB],
      awaiting: 2,
      totals: [],
    };

    expect(pendingIdsOf(day)).toEqual([1, 3]);
  });

  it('answers no id when the day holds no pending entry', () => {
    const day: ExpenseDay = {
      day: '2026-08-01',
      entries: [anExpense({ id: 1, status: 'RECORDED' }), anExpense({ id: 2, status: 'RECORDED' })],
      awaiting: 0,
      totals: [],
    };

    expect(pendingIdsOf(day)).toEqual([]);
  });
});

describe('the touched-days helper', () => {
  it('answers the two UTC days three ticked ids sit on, neither the day a reader’s own zone would name', () => {
    // UTC+14: 2026-07-31T23:30:00Z is still on the 31st at UTC, but already the 1st in this zone.
    vi.stubEnv('TZ', 'Pacific/Kiritimati');
    try {
      const first = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-07-31T23:30:00Z' });
      const second = anExpense({ id: 2, status: 'PENDING', createdAt: '2026-08-02T10:00:00Z' });
      const untouched = anExpense({ id: 3, status: 'PENDING', createdAt: '2026-08-03T10:00:00Z' });
      const page = anExpensePage([first, second, untouched]);

      const days = touchedDaysOf(page, [1, 2]);

      expect(days).toEqual(new Set(['2026-07-31', '2026-08-02']));
      // The reader's own zone would name the first entry's day 2026-08-01, not 2026-07-31.
      expect(days.has('2026-08-01')).toBe(false);
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('contributes no day and throws nothing for ids naming entries the page no longer holds', () => {
    const page = anExpensePage([anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-01T09:00:00Z' })]);

    expect(() => touchedDaysOf(page, [999])).not.toThrow();
    expect(touchedDaysOf(page, [999])).toEqual(new Set());
  });

  it('answers only the pending entry’s day when a RECORDED and a PENDING entry share an id on two different UTC days', () => {
    const recorded = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const pending = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-02T09:00:00Z' });
    const page = anExpensePage([recorded, pending]);

    expect(touchedDaysOf(page, [1])).toEqual(new Set(['2026-08-02']));
  });
});

describe('the day merge', () => {
  it('replaces only the touched day’s entries and dayTotals figure, leaving the other days and limit, offset and total as the original page’s', () => {
    const dayOneEntry = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const staleB = anExpense({ id: 2, status: 'PENDING', createdAt: '2026-08-02T09:00:00Z' });
    const staleC = anExpense({ id: 3, status: 'PENDING', createdAt: '2026-08-02T10:00:00Z' });
    const dayThreeEntry = anExpense({ id: 4, status: 'RECORDED', createdAt: '2026-08-03T09:00:00Z' });
    const page: ExpensePage = anExpensePage([dayOneEntry, staleB, staleC, dayThreeEntry], {
      dayTotals: [
        { day: '2026-08-01', amounts: [{ amount: '1.00', currency: 'EUR', separator: '' }] },
        { day: '2026-08-02', amounts: [{ amount: '99.00', currency: 'EUR', separator: '' }] },
        { day: '2026-08-03', amounts: [{ amount: '4.00', currency: 'EUR', separator: '' }] },
      ],
      limit: 50,
      offset: 20,
      total: 4,
    });

    const freshB = anExpense({ id: 2, status: 'RECORDED', createdAt: '2026-08-02T09:00:00Z' });
    const fresh: ExpensePage = anExpensePage([freshB], {
      dayTotals: [
        { day: '2026-08-02', amounts: [{ amount: '9.00', currency: 'EUR', separator: '' }] },
      ],
      limit: 10,
      offset: 0,
      total: 1,
    });

    const merged = mergeDay(page, '2026-08-02', fresh);

    expect(itemsOnDay(merged.items, '2026-08-01')).toEqual([dayOneEntry]);
    expect(itemsOnDay(merged.items, '2026-08-03')).toEqual([dayThreeEntry]);
    expect(itemsOnDay(merged.items, '2026-08-02')).toEqual([freshB]);
    expect(merged.dayTotals.find((dt) => dt.day === '2026-08-01')).toEqual(page.dayTotals[0]);
    expect(merged.dayTotals.find((dt) => dt.day === '2026-08-03')).toEqual(page.dayTotals[2]);
    expect(merged.dayTotals.find((dt) => dt.day === '2026-08-02')).toEqual(fresh.dayTotals[0]);
    expect(merged.limit).toBe(page.limit);
    expect(merged.offset).toBe(page.offset);
    expect(merged.total).toBe(page.total);
  });

  it('carries every entry a fresh read answers for a day, even ones the original page had cut', () => {
    const cut = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-01T09:00:00Z' });
    const page = anExpensePage([cut]);

    const second = anExpense({ id: 2, status: 'PENDING', createdAt: '2026-08-01T10:00:00Z' });
    const third = anExpense({ id: 3, status: 'PENDING', createdAt: '2026-08-01T11:00:00Z' });
    const fresh = anExpensePage([cut, second, third]);

    const merged = mergeDay(page, '2026-08-01', fresh);

    expect(itemsOnDay(merged.items, '2026-08-01')).toEqual([cut, second, third]);
  });

  it('drops a day from the merged page rather than leaving it showing entries that moved, when the fresh read holds nothing for it', () => {
    const dayOneEntry = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T09:00:00Z' });
    const dayTwoEntry = anExpense({ id: 2, status: 'PENDING', createdAt: '2026-08-02T09:00:00Z' });
    const page = anExpensePage([dayOneEntry, dayTwoEntry], {
      dayTotals: [
        { day: '2026-08-01', amounts: [{ amount: '1.00', currency: 'EUR', separator: '' }] },
      ],
    });

    const fresh = anExpensePage([], { total: 0 });

    const merged = mergeDay(page, '2026-08-02', fresh);

    expect(itemsOnDay(merged.items, '2026-08-02')).toEqual([]);
    expect(merged.dayTotals.find((dt) => dt.day === '2026-08-02')).toBeUndefined();
    expect(itemsOnDay(merged.items, '2026-08-01')).toEqual([dayOneEntry]);
  });
});

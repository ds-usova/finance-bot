import { describe, expect, it, vi } from 'vitest';
import type { DayTotal } from '../api/expenses';
import { anExpense } from '../testing/fixtures';
import { relativeDay, toDaySections } from './expenseDays';

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

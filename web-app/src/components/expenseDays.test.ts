import { describe, expect, it, vi } from 'vitest';
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

    const sections = toDaySections([newestA, newestB, middle, oldest]);

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

      const sections = toDaySections([entry]);

      expect(sections).toHaveLength(1);
      expect(sections[0]?.day).toBe('2026-08-01');
    } finally {
      vi.unstubAllEnvs();
    }
  });

  it('sums a day’s RECORDED entries into one EUR total when every entry shares that currency', () => {
    const first = anExpense({
      id: 1,
      status: 'RECORDED',
      createdAt: '2026-08-01T08:00:00Z',
      amountMinorUnits: 100,
      currency: 'EUR',
    });
    const second = anExpense({
      id: 2,
      status: 'RECORDED',
      createdAt: '2026-08-01T09:00:00Z',
      amountMinorUnits: 200,
      currency: 'EUR',
    });
    const third = anExpense({
      id: 3,
      status: 'RECORDED',
      createdAt: '2026-08-01T10:00:00Z',
      amountMinorUnits: 300,
      currency: 'EUR',
    });

    const sections = toDaySections([first, second, third]);

    expect(sections[0]?.totals).toEqual([{ currency: 'EUR', minorUnits: 600 }]);
  });

  it('keeps one total per currency, converting neither into the other', () => {
    const eur = anExpense({
      id: 1,
      status: 'RECORDED',
      createdAt: '2026-08-01T08:00:00Z',
      amountMinorUnits: 100,
      currency: 'EUR',
    });
    const usd = anExpense({
      id: 2,
      status: 'RECORDED',
      createdAt: '2026-08-01T09:00:00Z',
      amountMinorUnits: 200,
      currency: 'USD',
    });

    const sections = toDaySections([eur, usd]);

    expect(sections[0]?.totals).toHaveLength(2);
    expect(sections[0]?.totals).toEqual(
      expect.arrayContaining([
        { currency: 'EUR', minorUnits: 100 },
        { currency: 'USD', minorUnits: 200 },
      ]),
    );
  });

  it('sums only the RECORDED entries while still counting every entry and every one still awaiting a decision', () => {
    const recordedA = anExpense({
      id: 1,
      status: 'RECORDED',
      createdAt: '2026-08-01T08:00:00Z',
      amountMinorUnits: 100,
      currency: 'EUR',
    });
    const recordedB = anExpense({
      id: 2,
      status: 'RECORDED',
      createdAt: '2026-08-01T09:00:00Z',
      amountMinorUnits: 200,
      currency: 'EUR',
    });
    const pending = anExpense({
      id: 3,
      status: 'PENDING',
      createdAt: '2026-08-01T10:00:00Z',
      amountMinorUnits: 999,
      currency: 'EUR',
    });

    const sections = toDaySections([recordedA, recordedB, pending]);

    expect(sections[0]?.totals).toEqual([{ currency: 'EUR', minorUnits: 300 }]);
    expect(sections[0]?.entries).toHaveLength(3);
    expect(sections[0]?.awaiting).toBe(1);
  });

  it('carries no total for a day holding only an entry still awaiting a decision', () => {
    const pending = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-01T08:00:00Z' });

    const sections = toDaySections([pending]);

    expect(sections[0]?.totals).toEqual([]);
    expect(sections[0]?.entries).toHaveLength(1);
    expect(sections[0]?.awaiting).toBe(1);
  });

  it('keeps a RECORDED entry and a PENDING proposal that share an id, since status is part of an entry’s identity', () => {
    const recorded = anExpense({ id: 1, status: 'RECORDED', createdAt: '2026-08-01T08:00:00Z' });
    const pending = anExpense({ id: 1, status: 'PENDING', createdAt: '2026-08-01T09:00:00Z' });

    const sections = toDaySections([recorded, pending]);

    expect(sections[0]?.entries).toEqual([recorded, pending]);
  });

  it('answers no sections for an empty page', () => {
    expect(toDaySections([])).toEqual([]);
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

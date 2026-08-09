import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { DayTotal } from '../api/expenses';
import { substituteCatalogue } from '../testing/catalogue';
import { anExpense, anExpensePage } from '../testing/fixtures';
import { ExpenseList } from './ExpenseList';

const categoryNames = new Map([
  [10, 'Groceries'],
  [20, 'Transport'],
]);

function threeDaysOfEntries() {
  return [
    anExpense({
      id: 1,
      description: 'coffee',
      money: { amount: '3.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-01T09:00:00Z',
    }),
    anExpense({
      id: 2,
      description: 'lunch',
      money: { amount: '5.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-02T09:00:00Z',
    }),
    anExpense({
      id: 3,
      description: 'taxi',
      money: { amount: '9.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-03T09:00:00Z',
    }),
  ];
}

describe('the expense list', () => {
  it('groups a page of entries recorded across three UTC days into three sections, all closed', () => {
    render(
      <ExpenseList
        page={anExpensePage(threeDaysOfEntries())}
        categoryNames={categoryNames}
        tickedIds={new Set()}
        onTick={vi.fn()}
        onTickDay={vi.fn()}
        atBound={false}
      />,
    );

    expect(screen.getAllByRole('button')).toHaveLength(3);
    expect(screen.queryByText('coffee')).not.toBeInTheDocument();
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('taxi')).not.toBeInTheDocument();
  });

  it('opens one section on its own, leaving the other two closed', async () => {
    const user = userEvent.setup();

    render(
      <ExpenseList
        page={anExpensePage(threeDaysOfEntries())}
        categoryNames={categoryNames}
        tickedIds={new Set()}
        onTick={vi.fn()}
        onTickDay={vi.fn()}
        atBound={false}
      />,
    );

    const headers = screen.getAllByRole('button');
    expect(headers).toHaveLength(3);
    // toDaySections orders newest first, so the first header is 2026-08-03's, holding "taxi".
    await user.click(headers[0]!);

    expect(screen.getByText('taxi')).toBeInTheDocument();
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('coffee')).not.toBeInTheDocument();
  });

  it('shows a day’s own figure in its heading and shows none for a day with no figure', () => {
    const dayTotals: DayTotal[] = [
      { day: '2026-08-03', amounts: [{ amount: '12.34', currency: 'EUR', separator: '' }] },
      { day: '2026-08-01', amounts: [{ amount: '56.78', currency: 'EUR', separator: '' }] },
    ];

    render(
      <ExpenseList
        page={anExpensePage(threeDaysOfEntries(), { dayTotals })}
        categoryNames={categoryNames}
        tickedIds={new Set()}
        onTick={vi.fn()}
        onTickDay={vi.fn()}
        atBound={false}
      />,
    );

    const headers = screen.getAllByRole('button');
    // toDaySections orders newest first: [0] is 2026-08-03, [1] is 2026-08-02, [2] is 2026-08-01.
    expect(headers[0]).toHaveTextContent('EUR12.34');
    expect(headers[2]).toHaveTextContent('EUR56.78');
    expect(headers[1]).not.toHaveTextContent('EUR');
  });

  it('shows the catalogue’s substituted text for the empty state, once the catalogue is swapped', () => {
    substituteCatalogue();

    render(
      <ExpenseList
        page={anExpensePage([], { total: 0 })}
        categoryNames={categoryNames}
        tickedIds={new Set()}
        onTick={vi.fn()}
        onTickDay={vi.fn()}
        atBound={false}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('‹No expenses to show.›');
  });

  it('says there is nothing to show when the page holds no entries', () => {
    render(
      <ExpenseList
        page={anExpensePage([], { total: 0 })}
        categoryNames={categoryNames}
        tickedIds={new Set()}
        onTick={vi.fn()}
        onTickDay={vi.fn()}
        atBound={false}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('No expenses to show.');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

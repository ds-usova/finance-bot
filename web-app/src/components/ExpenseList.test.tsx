import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
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
      amountMinorUnits: 300,
      currency: 'EUR',
      createdAt: '2026-08-01T09:00:00Z',
    }),
    anExpense({
      id: 2,
      description: 'lunch',
      amountMinorUnits: 500,
      currency: 'EUR',
      createdAt: '2026-08-02T09:00:00Z',
    }),
    anExpense({
      id: 3,
      description: 'taxi',
      amountMinorUnits: 900,
      currency: 'EUR',
      createdAt: '2026-08-03T09:00:00Z',
    }),
  ];
}

describe('the expense list', () => {
  it('groups a page of entries recorded across three UTC days into three expanded sections', () => {
    render(
      <ExpenseList page={anExpensePage(threeDaysOfEntries())} categoryNames={categoryNames} />,
    );

    expect(screen.getAllByRole('button')).toHaveLength(3);
    expect(screen.getByText('coffee')).toBeInTheDocument();
    expect(screen.getByText('lunch')).toBeInTheDocument();
    expect(screen.getByText('taxi')).toBeInTheDocument();
  });

  it('collapses one section on its own, leaving the other two expanded', async () => {
    const user = userEvent.setup();

    render(
      <ExpenseList page={anExpensePage(threeDaysOfEntries())} categoryNames={categoryNames} />,
    );

    const headers = screen.getAllByRole('button');
    expect(headers).toHaveLength(3);
    // toDaySections orders newest first, so the first header is 2026-08-03's, holding "taxi".
    await user.click(headers[0]!);

    expect(screen.queryByText('taxi')).not.toBeInTheDocument();
    expect(screen.getByText('lunch')).toBeInTheDocument();
    expect(screen.getByText('coffee')).toBeInTheDocument();
  });

  it('shows the catalogue’s substituted text for the empty state, once the catalogue is swapped', () => {
    substituteCatalogue();

    render(<ExpenseList page={anExpensePage([], { total: 0 })} categoryNames={categoryNames} />);

    expect(screen.getByRole('alert')).toHaveTextContent('‹No expenses to show.›');
  });

  it('says there is nothing to show when the page holds no entries', () => {
    render(<ExpenseList page={anExpensePage([], { total: 0 })} categoryNames={categoryNames} />);

    expect(screen.getByRole('alert')).toHaveTextContent('No expenses to show.');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });
});

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { anExpense, anExpensePage } from '../testing/fixtures';
import { ExpenseList } from './ExpenseList';

const categoryNames = new Map([
  [10, 'Groceries'],
  [20, 'Transport'],
]);

function rowTexts(): string[] {
  return screen.getAllByRole('row').map((row) => row.textContent ?? '');
}

describe('the expense list', () => {
  it('renders a row per entry, in the order the page gives them, showing what each holds', () => {
    const lunch = anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'lunch',
      amountMinorUnits: 1250,
      currency: 'EUR',
    });
    const taxi = anExpense({
      id: 2,
      status: 'PENDING',
      categoryId: 20,
      description: 'taxi',
      merchant: 'City Cabs',
      amountMinorUnits: 900,
      currency: 'EUR',
    });

    render(<ExpenseList page={anExpensePage([lunch, taxi])} categoryNames={categoryNames} />);

    const texts = rowTexts();
    const lunchAt = texts.findIndex((text) => text.includes('lunch'));
    const taxiAt = texts.findIndex((text) => text.includes('taxi'));
    expect(lunchAt).toBeGreaterThanOrEqual(0);
    expect(taxiAt).toBeGreaterThan(lunchAt);

    const lunchRow = screen.getByRole('row', { name: /lunch/ });
    expect(lunchRow).toHaveTextContent('12.50');
    expect(lunchRow).toHaveTextContent('EUR');
    expect(lunchRow).toHaveTextContent(/recorded/i);
    expect(lunchRow).toHaveTextContent('Groceries');

    const taxiRow = screen.getByRole('row', { name: /taxi/ });
    expect(taxiRow).toHaveTextContent('9.00');
    expect(taxiRow).toHaveTextContent('EUR');
    expect(taxiRow).toHaveTextContent(/pending/i);
    expect(taxiRow).toHaveTextContent('Transport');
  });

  it('leaves the category unnamed when the lookup does not answer it, rather than failing', () => {
    const orphan = anExpense({
      id: 3,
      categoryId: 99,
      description: 'stamps',
      amountMinorUnits: 500,
      currency: 'EUR',
    });

    render(<ExpenseList page={anExpensePage([orphan])} categoryNames={categoryNames} />);

    const row = screen.getByRole('row', { name: /stamps/ });
    expect(row).toHaveTextContent('5.00');
    expect(row).toHaveTextContent('EUR');
    expect(row).not.toHaveTextContent('Groceries');
    expect(row).not.toHaveTextContent('99');
  });

  it('renders an entry with no merchant without an empty field in its place', () => {
    const coffee = anExpense({
      id: 4,
      categoryId: 10,
      description: 'coffee',
      merchant: 'Corner Cafe',
      amountMinorUnits: 300,
    });
    const stamps = anExpense({
      id: 5,
      categoryId: 10,
      description: 'stamps',
      merchant: null,
      amountMinorUnits: 500,
    });

    render(<ExpenseList page={anExpensePage([coffee, stamps])} categoryNames={categoryNames} />);

    expect(screen.getByRole('row', { name: /coffee/ })).toHaveTextContent('Corner Cafe');

    const row = screen.getByRole('row', { name: /stamps/ });
    expect(row).toHaveTextContent('5.00');
    expect(row).not.toHaveTextContent(/null|undefined/);
  });

  it('says there is nothing to show when the page holds no entries', () => {
    render(<ExpenseList page={anExpensePage([], { total: 0 })} categoryNames={categoryNames} />);

    expect(screen.getByText(/no expenses/i)).toBeInTheDocument();
  });
});

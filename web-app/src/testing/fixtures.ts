import type { Acceptance, Category, Expense, ExpensePage, Grouping } from '../api/expenses';
import type { AuthContextValue } from '../auth/authContext';
import type { ExpenseDay } from '../components/expenseDays';

/** The category names a listing test renders against, by id. */
export const categoryNames = new Map([
  [10, 'Groceries'],
  [20, 'Transport'],
]);

export function anExpense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 1,
    status: 'RECORDED',
    categoryId: 10,
    description: 'lunch',
    merchant: 'Corner Cafe',
    money: { amount: '12.50', currency: '€', separator: '' },
    createdAt: '2026-08-01T12:00:00Z',
    ...overrides,
  };
}

export function anExpensePage(
  items: Expense[] = [anExpense()],
  overrides: Partial<Omit<ExpensePage, 'items'>> = {},
): ExpensePage {
  return {
    items,
    limit: 50,
    offset: 0,
    total: items.length,
    dayTotals: [],
    ...overrides,
  };
}

export function aDay(overrides: Partial<ExpenseDay> = {}): ExpenseDay {
  return {
    day: '2026-08-01',
    entries: [],
    awaiting: 0,
    totals: [],
    ...overrides,
  };
}

export function aCategory(overrides: Partial<Category> = {}): Category {
  return {
    id: 10,
    name: 'Groceries',
    groupingId: 100,
    groupingName: 'Everyday',
    ...overrides,
  };
}

export function aGrouping(overrides: Partial<Grouping> = {}): Grouping {
  return {
    id: 100,
    name: 'Everyday',
    ...overrides,
  };
}

export function anAcceptance(overrides: Partial<Acceptance> = {}): Acceptance {
  return {
    accepted: 1,
    missing: 0,
    ...overrides,
  };
}

export function anAuthContext(overrides: Partial<AuthContextValue> = {}): AuthContextValue {
  return {
    status: 'authenticated',
    session: { externalId: '42' },
    signIn: async () => {},
    signOut: async () => {},
    sessionExpired: () => {},
    ...overrides,
  };
}

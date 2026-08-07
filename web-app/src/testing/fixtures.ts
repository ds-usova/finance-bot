import type { Category, Expense, ExpensePage, Grouping } from '../api/expenses';
import type { AuthContextValue } from '../auth/authContext';

export function anExpense(overrides: Partial<Expense> = {}): Expense {
  return {
    id: 1,
    status: 'RECORDED',
    categoryId: 10,
    description: 'lunch',
    merchant: 'Corner Cafe',
    amountMinorUnits: 1250,
    currency: 'EUR',
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

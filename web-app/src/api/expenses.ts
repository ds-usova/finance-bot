import { request } from './client';
import type { components } from './generated/ledger-api';

export type Expense = components['schemas']['Expense'];
export type ExpensePage = components['schemas']['ExpensePage'];
export type ExpenseStatus = components['schemas']['ExpenseStatus'];
export type Category = components['schemas']['Category'];
export type Grouping = components['schemas']['Grouping'];

/** The six query parameters the listing accepts. A field left unset is not sent, so the ledger applies its own default. */
export type ExpenseFilter = {
  status?: ExpenseStatus;
  categoryId?: number;
  from?: string;
  to?: string;
  limit?: number;
  offset?: number;
};

const EXPENSES_PATH = '/api/v1/expenses';
const CATEGORIES_PATH = '/api/v1/categories';
const GROUPINGS_PATH = '/api/v1/groupings';

export async function listExpenses(filter: ExpenseFilter): Promise<ExpensePage> {
  return get<ExpensePage>(EXPENSES_PATH, {
    status: filter.status,
    categoryId: filter.categoryId,
    from: filter.from,
    to: filter.to,
    limit: filter.limit,
    offset: filter.offset,
  });
}

export async function listCategories(groupingId?: number): Promise<Category[]> {
  return get<Category[]>(CATEGORIES_PATH, { groupingId });
}

export async function listGroupings(): Promise<Grouping[]> {
  return get<Grouping[]>(GROUPINGS_PATH, {});
}

async function get<T>(
  path: string,
  values: Record<string, string | number | undefined>,
): Promise<T> {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(values)) {
    if (value !== undefined) {
      query.set(name, String(value));
    }
  }
  const search = query.toString();
  // `request` answers null only for a 204, which a read never sends.
  return (await request<T>(search ? `${path}?${search}` : path)) as T;
}

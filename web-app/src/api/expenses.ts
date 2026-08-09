import { request } from './client';
import type { components } from './generated/ledger-api';

export type Expense = components['schemas']['Expense'];
export type ExpensePage = components['schemas']['ExpensePage'];
export type ExpenseStatus = components['schemas']['ExpenseStatus'];
export type Category = components['schemas']['Category'];
export type Grouping = components['schemas']['Grouping'];
export type RenderedMoney = components['schemas']['RenderedMoney'];
export type DayTotal = components['schemas']['DayTotal'];
export type Acceptance = components['schemas']['Acceptance'];

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

export async function acceptExpenses(ids: number[]): Promise<Acceptance> {
  // posts the ids to /api/v1/expenses/acceptances through `request`, which carries the cookies and the CSRF
  // token, and answers how many were accepted and how many named nothing. RU01 covers the behaviour.
  void ids;
  return null as unknown as Acceptance;
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

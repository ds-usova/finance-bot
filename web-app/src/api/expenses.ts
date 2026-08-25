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
export type CategoryPatch = components['schemas']['CategoryPatch'];

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
const ACCEPTANCES_PATH = '/api/v1/expenses/acceptances';

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
  const acceptance = await request<Acceptance>(ACCEPTANCES_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ids }),
  });
  if (!acceptance) {
    throw new Error('the acceptance answered with no counts');
  }
  return acceptance;
}

export async function changeCategory(entry: Expense, categoryId: number): Promise<Expense> {
  const patch: CategoryPatch = [{ op: 'replace', path: '/categoryId', value: categoryId }];
  const updated = await request<Expense>(`${EXPENSES_PATH}/${entry.id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json-patch+json' },
    body: JSON.stringify(patch),
  });
  if (!updated) {
    throw new Error('the changed entry answered with no body');
  }
  return updated;
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

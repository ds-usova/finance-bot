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

export async function listExpenses(filter: ExpenseFilter): Promise<ExpensePage> {
  // builds the query string from the filter's set fields — and only those — and requests
  // GET /api/v1/expenses, returning the answered page as it stands and letting an ApiError reach the caller
  throw new Error(`not implemented: listExpenses(${JSON.stringify(filter)})`);
}

export async function listCategories(groupingId?: number): Promise<Category[]> {
  // requests GET /api/v1/categories, carrying groupingId as the only query parameter when one is given
  throw new Error(`not implemented: listCategories(${String(groupingId)})`);
}

export async function listGroupings(): Promise<Grouping[]> {
  // requests GET /api/v1/groupings with no query string, returning every grouping the ledger answered
  throw new Error('not implemented: listGroupings()');
}

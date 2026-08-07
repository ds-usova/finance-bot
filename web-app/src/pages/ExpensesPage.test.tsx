import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { listCategories, listExpenses, listGroupings } from '../api/expenses';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { aCategory, aGrouping, anExpense, anExpensePage } from '../testing/fixtures';
import { ExpensesPage } from './ExpensesPage';

vi.mock('../api/expenses', () => ({
  listExpenses: vi.fn(),
  listCategories: vi.fn(),
  listGroupings: vi.fn(),
}));

const listExpensesMock = vi.mocked(listExpenses);
const listCategoriesMock = vi.mocked(listCategories);
const listGroupingsMock = vi.mocked(listGroupings);

const page = anExpensePage([anExpense({ description: 'lunch', categoryId: 10 })]);
const categories = [aCategory({ id: 10, name: 'Groceries', groupingId: 100 })];
const groupings = [aGrouping({ id: 100, name: 'Everyday' })];

function renderPage(context: Partial<AuthContextValue> = {}) {
  const value: AuthContextValue = {
    status: 'authenticated',
    session: { externalId: '987654321' },
    signIn: async () => {},
    signOut: async () => {},
    sessionExpired: () => {},
    ...context,
  };

  return render(
    <AuthContext.Provider value={value}>
      <ExpensesPage />
    </AuthContext.Provider>,
  );
}

describe('the expenses page', () => {
  beforeEach(() => {
    listExpensesMock.mockResolvedValue(page);
    listCategoriesMock.mockResolvedValue(categories);
    listGroupingsMock.mockResolvedValue(groupings);
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('reads the listing, the categories and the groupings once and renders what they answered', async () => {
    renderPage();

    expect(await screen.findByText('lunch')).toBeInTheDocument();
    expect(listExpensesMock).toHaveBeenCalledOnce();
    expect(listCategoriesMock).toHaveBeenCalledOnce();
    expect(listGroupingsMock).toHaveBeenCalledOnce();

    const groupingControl = screen.getByRole('combobox', { name: /grouping/i });
    expect(within(groupingControl).getByRole('option', { name: /Everyday/ })).toBeInTheDocument();
    const categoryControl = screen.getByRole('combobox', { name: /category/i });
    expect(within(categoryControl).getByRole('option', { name: /Groceries/ })).toBeInTheDocument();
  });

  it('repeats only the listing when the filter changes, keeping the tree it already holds', async () => {
    renderPage();
    await screen.findByText('lunch');

    const categoryControl = screen.getByRole('combobox', { name: /category/i });
    await userEvent.selectOptions(
      categoryControl,
      within(categoryControl).getByRole('option', { name: /Groceries/ }),
    );

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expect(listExpensesMock).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 10 }));
    expect(listCategoriesMock).toHaveBeenCalledOnce();
    expect(listGroupingsMock).toHaveBeenCalledOnce();
  });

  it('reports an expired session to the context when the listing is refused', async () => {
    listExpensesMock.mockRejectedValue(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
  });

  it('shows a refused filter’s message while the list that was already there still stands', async () => {
    listExpensesMock
      .mockResolvedValueOnce(page)
      .mockRejectedValueOnce(new ApiError(400, 'from must be a date'));
    renderPage();
    await screen.findByText('lunch');

    const categoryControl = screen.getByRole('combobox', { name: /category/i });
    await userEvent.selectOptions(
      categoryControl,
      within(categoryControl).getByRole('option', { name: /Groceries/ }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('from must be a date');
    expect(screen.getByText('lunch')).toBeInTheDocument();
  });

  it('keeps the session when the listing fails for a reason other than an expiry', async () => {
    listExpensesMock.mockRejectedValue(new ApiError(503, 'the ledger is temporarily unavailable'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'the ledger is temporarily unavailable',
    );
    expect(sessionExpired).not.toHaveBeenCalled();
  });

  it('reports an expired session when the categories read is refused', async () => {
    listCategoriesMock.mockRejectedValue(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
  });

  it('reports an expired session when the groupings read is refused', async () => {
    listGroupingsMock.mockRejectedValue(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
  });

  it('still lists the expenses, with their categories unnamed, when the categories read fails', async () => {
    listCategoriesMock.mockRejectedValue(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );

    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'the ledger is temporarily unavailable',
    );
    expect(screen.getByText('lunch')).toBeInTheDocument();
    expect(screen.queryByText('Groceries')).not.toBeInTheDocument();
  });

  it('ends the session through the context when the sign-out control is used', async () => {
    const signOut = vi.fn().mockResolvedValue(undefined);

    renderPage({ signOut });
    await screen.findByText('lunch');
    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(signOut).toHaveBeenCalledOnce();
  });
});

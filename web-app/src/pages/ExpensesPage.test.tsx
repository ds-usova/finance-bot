import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { listCategories, listExpenses, listGroupings, type ExpensePage } from '../api/expenses';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { chooseOption } from '../testing/combobox';
import { aCategory, aGrouping, anAuthContext, anExpense, anExpensePage } from '../testing/fixtures';
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

/** A read the test settles itself, so a slower one can be made to answer after a faster one. */
function inFlight() {
  let answer!: (page: ExpensePage) => void;
  let refuse!: (error: unknown) => void;
  const promise = new Promise<ExpensePage>((resolve, reject) => {
    answer = resolve;
    refuse = reject;
  });
  return { promise, answer, refuse };
}

async function chooseGroceries() {
  await chooseOption(/category/i, /Groceries/);
}

function renderPage(context: Partial<AuthContextValue> = {}) {
  const value = anAuthContext({ session: { externalId: '987654321' }, ...context });

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

    await userEvent.click(screen.getByRole('combobox', { name: /grouping/i }));
    expect(await screen.findByRole('option', { name: /Everyday/ })).toBeInTheDocument();
    await userEvent.keyboard('{Escape}');

    await userEvent.click(screen.getByRole('combobox', { name: /category/i }));
    expect(await screen.findByRole('option', { name: /Groceries/ })).toBeInTheDocument();
  });

  it('repeats only the listing when the filter changes, keeping the tree it already holds', async () => {
    renderPage();
    await screen.findByText('lunch');

    await chooseGroceries();

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

    await chooseGroceries();

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

  it('reads the page after the one on screen when the pager steps forward, keeping the filter', async () => {
    const firstPage = anExpensePage([anExpense({ description: 'lunch', categoryId: 10 })], {
      limit: 1,
      offset: 0,
      total: 3,
    });
    listExpensesMock.mockResolvedValue(firstPage);
    renderPage();
    await screen.findByText('lunch');

    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    await userEvent.click(screen.getByRole('button', { name: /next/i }));

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ offset: 1, categoryId: 10 }),
    );
  });

  it('returns to the first page when the filter changes', async () => {
    listExpensesMock.mockResolvedValue(
      anExpensePage([anExpense({ description: 'lunch', categoryId: 10 })], {
        limit: 1,
        offset: 0,
        total: 3,
      }),
    );
    renderPage();
    await screen.findByText('lunch');
    await userEvent.click(screen.getByRole('button', { name: /next/i }));
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));

    await chooseGroceries();

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 10 }));
    expect(listExpensesMock.mock.lastCall?.[0].offset).toBeUndefined();
  });

  it('leaves the newer answer standing when a read the filter has moved on from answers last', async () => {
    const left = inFlight();
    listExpensesMock
      .mockReturnValueOnce(left.promise)
      .mockResolvedValueOnce(anExpensePage([anExpense({ id: 2, description: 'taxi' })]));

    renderPage();
    await chooseGroceries();
    await screen.findByText('taxi');

    await act(async () => {
      left.answer(anExpensePage([anExpense({ id: 1, description: 'lunch' })]));
    });

    expect(screen.getByText('taxi')).toBeInTheDocument();
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
  });

  it('does not end the session over a refusal to a read the filter has moved on from', async () => {
    const left = inFlight();
    listExpensesMock.mockReturnValueOnce(left.promise).mockResolvedValueOnce(page);
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });
    await chooseGroceries();
    await screen.findByText('lunch');

    await act(async () => {
      left.refuse(new ApiError(401, 'no session'));
    });

    expect(sessionExpired).not.toHaveBeenCalled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('offers no pager when the listing answers everything the filter matches', async () => {
    renderPage();
    await screen.findByText('lunch');

    expect(screen.queryByRole('button', { name: /next/i })).not.toBeInTheDocument();
  });
});

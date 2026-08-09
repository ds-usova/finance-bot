import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import {
  acceptExpenses,
  listCategories,
  listExpenses,
  listGroupings,
  type Acceptance,
  type ExpensePage,
} from '../api/expenses';
import { AuthContext, type AuthContextValue } from '../auth/authContext';
import { expandDays, listingArrives } from '../testing/accordion';
import { chooseFromList } from '../testing/combobox';
import {
  aCategory,
  aGrouping,
  anAcceptance,
  anAuthContext,
  anExpense,
  anExpensePage,
} from '../testing/fixtures';
import { ExpensesPage } from './ExpensesPage';

vi.mock('../api/expenses', () => ({
  listExpenses: vi.fn(),
  listCategories: vi.fn(),
  listGroupings: vi.fn(),
  acceptExpenses: vi.fn(),
}));

const listExpensesMock = vi.mocked(listExpenses);
const listCategoriesMock = vi.mocked(listCategories);
const listGroupingsMock = vi.mocked(listGroupings);
const acceptExpensesMock = vi.mocked(acceptExpenses);

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

/** The same, for an acceptance call the test settles itself. */
function inFlightAcceptance() {
  let answer!: (acceptance: Acceptance) => void;
  let refuse!: (error: unknown) => void;
  const promise = new Promise<Acceptance>((resolve, reject) => {
    answer = resolve;
    refuse = reject;
  });
  return { promise, answer, refuse };
}

async function chooseGroceries() {
  await chooseFromList(/^category/i, /Groceries/);
}

/** Ticks the checkbox of the entry named, scoped to its own row so the generic checkbox label cannot match
 * another entry's. */
async function tickEntry(name: string | RegExp) {
  await userEvent.click(within(screen.getByRole('listitem', { name })).getByRole('checkbox'));
}

/** The listing arriving, then every section opened, for a test that asserts on the entries themselves. */
async function listedEntries(): Promise<void> {
  await listingArrives();
  expandDays();
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
    acceptExpensesMock.mockResolvedValue(anAcceptance());
  });

  afterEach(() => {
    vi.resetAllMocks();
  });

  it('reads the listing, the categories and the groupings once and renders what they answered', async () => {
    renderPage();

    await listedEntries();
    expect(screen.getByText('lunch')).toBeInTheDocument();
    expect(listExpensesMock).toHaveBeenCalledOnce();
    expect(listCategoriesMock).toHaveBeenCalledOnce();
    expect(listGroupingsMock).toHaveBeenCalledOnce();

    // Both reads reach the one list: the category is an entry in it, the grouping is the heading over it.
    await userEvent.click(screen.getByRole('button', { name: /^category/i }));
    expect(await screen.findByRole('option', { name: /Groceries/ })).toBeInTheDocument();
    expect(screen.getByText('Everyday')).toBeInTheDocument();
  });

  it('repeats only the listing when the filter changes, keeping the tree it already holds', async () => {
    renderPage();
    await listingArrives();

    await chooseGroceries();

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expect(listExpensesMock).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 10 }));
    expect(listCategoriesMock).toHaveBeenCalledOnce();
    expect(listGroupingsMock).toHaveBeenCalledOnce();
    expect(acceptExpensesMock).not.toHaveBeenCalled();
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
    await listedEntries();

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
    expandDays();
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
    await listingArrives();

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
    await listingArrives();
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
    await listedEntries();

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
    await listingArrives();

    await act(async () => {
      left.refuse(new ApiError(401, 'no session'));
    });

    expect(sessionExpired).not.toHaveBeenCalled();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('offers no pager when the listing answers everything the filter matches', async () => {
    renderPage();
    await listingArrives();

    expect(screen.queryByRole('button', { name: /next/i })).not.toBeInTheDocument();
  });

  it('carries all three ticked ids in one call, clears the ticks and re-reads the two touched days', async () => {
    const lunch = anExpense({
      id: 1,
      description: 'lunch',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    const coffee = anExpense({
      id: 2,
      description: 'coffee',
      status: 'PENDING',
      createdAt: '2026-08-05T10:00:00Z',
    });
    const taxi = anExpense({
      id: 3,
      description: 'taxi',
      status: 'PENDING',
      createdAt: '2026-08-04T18:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch, coffee, taxi]));
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 3, missing: 0 }));

    renderPage();
    await listedEntries();
    await tickEntry(/lunch/i);
    await tickEntry(/coffee/i);
    await tickEntry(/taxi/i);

    await userEvent.click(screen.getByRole('button', { name: 'Accept 3 entries' }));

    expect(acceptExpensesMock).toHaveBeenCalledOnce();
    expect(acceptExpensesMock).toHaveBeenCalledWith([1, 2, 3]);
    expect(screen.queryByRole('button', { name: /accept/i })).not.toBeInTheDocument();

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expect(listExpensesMock).toHaveBeenLastCalledWith({
      status: undefined,
      categoryId: undefined,
      from: '2026-08-04',
      to: '2026-08-05',
      offset: undefined,
      limit: 100,
    });
  });

  it('reads back with no offset, and the touched day holding its entries, for an acceptance made from the second page', async () => {
    const firstItem = anExpense({
      id: 1,
      description: 'lunch',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    const secondItem = anExpense({
      id: 5,
      description: 'taxi',
      status: 'PENDING',
      createdAt: '2026-08-04T18:00:00Z',
    });
    const firstPage = anExpensePage([firstItem], { limit: 1, offset: 0, total: 2 });
    const secondPage = anExpensePage([secondItem], { limit: 1, offset: 1, total: 2 });
    listExpensesMock.mockResolvedValueOnce(firstPage).mockResolvedValueOnce(secondPage);
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 1, missing: 0 }));

    renderPage();
    await listedEntries();
    await userEvent.click(screen.getByRole('button', { name: /next/i }));
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expandDays();

    listExpensesMock.mockResolvedValueOnce(
      anExpensePage([secondItem], { limit: 1, offset: 1, total: 2 }),
    );
    await tickEntry(/taxi/i);
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock.mock.lastCall?.[0].offset).toBeUndefined();
    expandDays();
    expect(screen.getByText('taxi')).toBeInTheDocument();
  });

  it('replaces only the accepted day, leaves the other two untouched and keeps the pager as it was', async () => {
    const coffee = anExpense({
      id: 10,
      description: 'coffee',
      status: 'RECORDED',
      createdAt: '2026-08-03T09:00:00Z',
    });
    const taxi = anExpense({
      id: 11,
      description: 'taxi',
      status: 'PENDING',
      createdAt: '2026-08-04T09:00:00Z',
    });
    const hotel = anExpense({
      id: 12,
      description: 'hotel',
      status: 'RECORDED',
      createdAt: '2026-08-05T09:00:00Z',
    });
    const threeDayPage = anExpensePage([coffee, taxi, hotel], { limit: 3, offset: 0, total: 5 });
    listExpensesMock.mockResolvedValueOnce(threeDayPage);
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 1, missing: 0 }));

    renderPage();
    await listedEntries();
    const pagerTextBefore = screen.getByText(/showing/i).textContent;

    await tickEntry(/taxi/i);
    const acceptedTaxi = anExpense({
      id: 11,
      description: 'taxi',
      status: 'RECORDED',
      createdAt: '2026-08-04T09:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([acceptedTaxi], { limit: 100, total: 1 }));
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expandDays();

    expect(screen.getByText('coffee')).toBeInTheDocument();
    expect(screen.getByText('hotel')).toBeInTheDocument();
    const taxiItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(taxiItem).queryByText('Pending')).not.toBeInTheDocument();
    expect(screen.getByText(/showing/i).textContent).toEqual(pagerTextBefore);
    expect(screen.getByRole('button', { name: /next/i })).toBeInTheDocument();
  });

  it('tells the person in words how many had already moved on, and still reads the touched day back', async () => {
    const entry = anExpense({
      id: 20,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([entry]));
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 0, missing: 1 }));

    renderPage();
    await listedEntries();
    await tickEntry(/ferry/i);
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    expect(await screen.findByText('1 entry had already moved on')).toBeInTheDocument();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
  });

  it('shows the failure, leaves the ticks standing and the listing unchanged when the ledger refuses with 503', async () => {
    const entry = anExpense({
      id: 21,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([entry]));
    acceptExpensesMock.mockRejectedValueOnce(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );

    renderPage();
    await listedEntries();
    await tickEntry(/ferry/i);
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'the ledger is temporarily unavailable',
    );
    expect(screen.getByRole('button', { name: 'Accept 1 entry' })).toBeInTheDocument();
    expect(listExpensesMock).toHaveBeenCalledOnce();
  });

  it('reports an expired session and re-reads nothing when the ledger refuses the acceptance with 401', async () => {
    const entry = anExpense({
      id: 22,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([entry]));
    acceptExpensesMock.mockRejectedValueOnce(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });
    await listedEntries();
    await tickEntry(/ferry/i);
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
    expect(listExpensesMock).toHaveBeenCalledOnce();
  });

  it('keeps a tick, the action naming it and its id, across the day section closing and opening again', async () => {
    const entry = anExpense({
      id: 23,
      description: 'parking',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([entry]));
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 1, missing: 0 }));

    renderPage();
    await listedEntries();
    await tickEntry(/parking/i);

    const header = screen.getByRole('button', { expanded: true });
    await userEvent.click(header);
    await userEvent.click(header);

    expect(
      within(screen.getByRole('listitem', { name: /parking/i })).getByRole('checkbox'),
    ).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    expect(acceptExpensesMock).toHaveBeenCalledWith([23]);
  });

  it('makes only one call when the action is pressed again while the acceptance is still out', async () => {
    const entry = anExpense({
      id: 24,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([entry]));
    acceptExpensesMock.mockReturnValueOnce(new Promise(() => {}));

    renderPage();
    await listedEntries();
    await tickEntry(/ferry/i);
    const action = screen.getByRole('button', { name: 'Accept 1 entry' });

    await userEvent.click(action);
    await userEvent.click(action);

    expect(acceptExpensesMock).toHaveBeenCalledOnce();
  });

  it('tells the list the 100-id bound is reached, so a further tick past it is impossible and no request ever carries more', async () => {
    const inBound = Array.from({ length: 100 }, (_, i) =>
      anExpense({
        id: i + 1,
        description: `entry ${i + 1}`,
        status: 'PENDING',
        createdAt: '2026-08-03T09:00:00Z',
      }),
    );
    const overBound = Array.from({ length: 5 }, (_, i) =>
      anExpense({
        id: 200 + i,
        description: `later ${i + 1}`,
        status: 'PENDING',
        createdAt: '2026-08-05T09:00:00Z',
      }),
    );
    listExpensesMock.mockResolvedValue(
      anExpensePage([...inBound, ...overBound], { limit: 105, offset: 0, total: 105 }),
    );
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 100, missing: 0 }));

    renderPage();
    await listedEntries();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all 100 pending entries' }));

    expect(screen.getByRole('checkbox', { name: 'Select all 5 pending entries' })).toBeDisabled();
    expect(
      within(screen.getByRole('listitem', { name: /later 1/i })).getByRole('checkbox'),
    ).toBeDisabled();

    const action = screen.getByRole('button', { name: 'Accept 100 entries' });
    expect(action).toBeInTheDocument();
    await userEvent.click(action);

    expect(acceptExpensesMock).toHaveBeenCalledOnce();
    const [ids] = acceptExpensesMock.mock.calls[0] ?? [];
    expect(ids).toBeDefined();
    expect(ids).toHaveLength(100);
    expect(ids?.every((id) => inBound.some((entry) => entry.id === id))).toBe(true);
  });

  it('reads back against the filter the page holds now, not the one the acceptance call left with', async () => {
    const entry = anExpense({
      id: 25,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([entry]));
    const outstanding = inFlightAcceptance();
    acceptExpensesMock.mockReturnValueOnce(outstanding.promise);

    renderPage();
    await listedEntries();
    await tickEntry(/ferry/i);
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));

    listExpensesMock.mockResolvedValueOnce(anExpensePage([entry]));
    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));

    listExpensesMock.mockResolvedValueOnce(anExpensePage([entry]));
    await act(async () => {
      outstanding.answer(anAcceptance({ accepted: 1, missing: 0 }));
    });

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 10 }));
  });
});

import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import {
  acceptExpenses,
  changeCategory,
  listCategories,
  listExpenses,
  listGroupings,
  type Acceptance,
  type Expense,
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
  changeCategory: vi.fn(),
}));

const listExpensesMock = vi.mocked(listExpenses);
const listCategoriesMock = vi.mocked(listCategories);
const listGroupingsMock = vi.mocked(listGroupings);
const acceptExpensesMock = vi.mocked(acceptExpenses);
const changeCategoryMock = vi.mocked(changeCategory);

const page = anExpensePage([anExpense({ description: 'lunch', categoryId: 10 })]);
const categories = [aCategory({ id: 10, name: 'Groceries', groupingId: 100 })];
const groupings = [aGrouping({ id: 100, name: 'Everyday' })];

/** Both categories a "changed to a different one" scenario needs, under the one grouping the fixtures share. */
const twoCategories = [
  aCategory({ id: 10, name: 'Groceries', groupingId: 100 }),
  aCategory({ id: 20, name: 'Transport', groupingId: 100 }),
];

/** A call the test settles itself, so a slower one can be made to answer after a faster one. */
function inFlight<T>() {
  let answer!: (value: T) => void;
  let refuse!: (error: unknown) => void;
  const promise = new Promise<T>((resolve, reject) => {
    answer = resolve;
    refuse = reject;
  });
  return { promise, answer, refuse };
}

async function chooseGroceries() {
  await chooseFromList(/^category/i, /Groceries/);
}

/** Opens the row named by its description's own control — never the filter's, which is queried as
 * `/^category/i` throughout this file — and picks a category from it. */
async function changeCategoryOnRow(description: string, categoryName: string | RegExp) {
  await chooseFromList(new RegExp(`change ${description}`, 'i'), categoryName);
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

    // The filter's own trigger is the only control this pattern matches, with every day section open — a
    // row's own control is named with a verb and never collides with it.
    expect(screen.getAllByRole('button', { name: /^category/i })).toHaveLength(1);

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
    expect(changeCategoryMock).not.toHaveBeenCalled();
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
    expect(screen.queryByRole('button', { name: /change lunch/i })).not.toBeInTheDocument();
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
    const left = inFlight<ExpensePage>();
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
    const left = inFlight<ExpensePage>();
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

  it('disables the second day’s own checkbox once the first day’s whole tick leaves too little headroom, keeping its entries live and no call over the bound', async () => {
    const firstDay = Array.from({ length: 95 }, (_, i) =>
      anExpense({
        id: i + 1,
        description: `first ${i + 1}`,
        status: 'PENDING',
        createdAt: '2026-08-03T09:00:00Z',
      }),
    );
    const secondDay = Array.from({ length: 10 }, (_, i) =>
      anExpense({
        id: 500 + i,
        description: `second ${i + 1}`,
        status: 'PENDING',
        createdAt: '2026-08-05T09:00:00Z',
      }),
    );
    listExpensesMock.mockResolvedValue(
      anExpensePage([...secondDay, ...firstDay], { limit: 105, offset: 0, total: 105 }),
    );
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 95, missing: 0 }));

    renderPage();
    await listedEntries();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all 95 pending entries' }));

    expect(screen.getByRole('checkbox', { name: 'Select all 10 pending entries' })).toBeDisabled();
    for (let i = 1; i <= 10; i += 1) {
      expect(
        within(screen.getByRole('listitem', { name: new RegExp(`second ${i}$`, 'i') })).getByRole(
          'checkbox',
        ),
      ).toBeEnabled();
    }

    await userEvent.click(screen.getByRole('button', { name: 'Accept 95 entries' }));

    expect(acceptExpensesMock).toHaveBeenCalledOnce();
    const [ids] = acceptExpensesMock.mock.calls[0] ?? [];
    expect(ids).toHaveLength(95);
    expect(ids?.every((id) => firstDay.some((entry) => entry.id === id))).toBe(true);
  });

  it('reads back against the filter the page holds now, not the one the acceptance call left with', async () => {
    const entry = anExpense({
      id: 25,
      description: 'ferry',
      status: 'PENDING',
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([entry]));
    const outstanding = inFlight<Acceptance>();
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

  it('carries the entry and the chosen id in one change call, shows the new category on that row, and leaves the rest of the listing and the pager untouched', async () => {
    const lunch = anExpense({
      id: 1,
      description: 'lunch',
      categoryId: 10,
      createdAt: '2026-08-05T09:00:00Z',
    });
    const coffee = anExpense({
      id: 2,
      description: 'coffee',
      categoryId: 10,
      createdAt: '2026-08-05T10:00:00Z',
    });
    const taxi = anExpense({
      id: 3,
      description: 'taxi',
      categoryId: 10,
      createdAt: '2026-08-04T18:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(
      anExpensePage([lunch, coffee, taxi], { limit: 3, offset: 0, total: 5 }),
    );
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockResolvedValueOnce({ ...lunch, categoryId: 20 });

    renderPage();
    await listedEntries();
    const pagerTextBefore = screen.getByText(/showing/i).textContent;

    await changeCategoryOnRow('lunch', /Transport/);

    expect(changeCategoryMock).toHaveBeenCalledOnce();
    expect(changeCategoryMock).toHaveBeenCalledWith(lunch, 20);
    const lunchRow = await screen.findByRole('listitem', { name: /lunch/i });
    expect(within(lunchRow).getByText('Transport')).toBeInTheDocument();
    expect(within(lunchRow).queryByText('Groceries')).not.toBeInTheDocument();
    const coffeeRow = screen.getByRole('listitem', { name: /coffee/i });
    expect(within(coffeeRow).getByText('Groceries')).toBeInTheDocument();
    expect(listExpensesMock).toHaveBeenCalledOnce();
    expect(screen.getByText(/showing/i).textContent).toEqual(pagerTextBefore);
  });

  it('reads back only the day the answered entry carries, spanning it alone with no offset, when a narrowed listing loses the refiled row', async () => {
    const day = '2026-08-05T09:00:00Z';
    const lunch = anExpense({
      id: 30,
      status: 'RECORDED',
      description: 'lunch',
      categoryId: 10,
      createdAt: day,
    });
    const coffee = anExpense({
      id: 31,
      status: 'PENDING',
      description: 'coffee',
      categoryId: 10,
      createdAt: day,
    });
    // total is set above the item count so the pager renders at all — Pager.tsx shows nothing once every
    // item fits on the first page, which would otherwise hide the very value this case checks stays put.
    const narrowed = anExpensePage([lunch, coffee], { limit: 50, offset: 0, total: 5 });
    listExpensesMock.mockResolvedValueOnce(page).mockResolvedValueOnce(narrowed);
    listCategoriesMock.mockResolvedValue(twoCategories);

    renderPage();
    await listedEntries();
    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expandDays();

    changeCategoryMock.mockResolvedValueOnce({ ...lunch, categoryId: 20 });
    const freshTotal = {
      day: '2026-08-05',
      amounts: [{ amount: '9.00', currency: '€', separator: '' }],
    };
    // The fresh read's own limit/offset/total are decoys, deliberately different from the narrowed page's, so
    // a pager built from them rather than from the original page would be caught.
    listExpensesMock.mockResolvedValueOnce(
      anExpensePage([coffee], { limit: 99, offset: 99, total: 99, dayTotals: [freshTotal] }),
    );

    await changeCategoryOnRow('lunch', /Transport/);

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        categoryId: 10,
        from: '2026-08-05',
        to: '2026-08-05',
        offset: undefined,
      }),
    );
    expect(screen.queryByRole('listitem', { name: /lunch/i })).not.toBeInTheDocument();
    expect(await screen.findByRole('listitem', { name: /coffee/i })).toBeInTheDocument();
    expect(await screen.findByText('€9.00')).toBeInTheDocument();
    // Only coffee remains, so the range's upper bound is 1 — but the total is still the narrowed page's 5,
    // not the fresh read's decoy 99.
    expect(screen.getByText(/showing/i).textContent).toEqual('Showing 1–1 of 5.');
  });

  it('keeps a ticked pending row ticked and the action naming it, and accepting afterwards carries its id, once its category is changed', async () => {
    const parking = anExpense({
      id: 40,
      status: 'PENDING',
      description: 'parking',
      categoryId: 10,
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([parking]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockResolvedValueOnce({ ...parking, categoryId: 20 });
    acceptExpensesMock.mockResolvedValueOnce(anAcceptance({ accepted: 1, missing: 0 }));

    renderPage();
    await listedEntries();
    await tickEntry(/parking/i);

    await changeCategoryOnRow('parking', /Transport/);

    const parkingRow = await screen.findByRole('listitem', { name: /parking/i });
    expect(within(parkingRow).getByRole('checkbox')).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Accept 1 entry' }));
    expect(acceptExpensesMock).toHaveBeenCalledWith([40]);
  });

  it('drops the tick and the action no longer counts it, when a refiled ticked row leaves a narrowed listing', async () => {
    const day = '2026-08-05T09:00:00Z';
    const parking = anExpense({
      id: 41,
      status: 'PENDING',
      description: 'parking',
      categoryId: 10,
      createdAt: day,
    });
    const narrowed = anExpensePage([parking], { limit: 50, offset: 0, total: 1 });
    listExpensesMock.mockResolvedValueOnce(page).mockResolvedValueOnce(narrowed);
    listCategoriesMock.mockResolvedValue(twoCategories);

    renderPage();
    await listedEntries();
    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expandDays();
    await tickEntry(/parking/i);

    changeCategoryMock.mockResolvedValueOnce({ ...parking, categoryId: 20 });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([], { limit: 50, offset: 0, total: 0 }));

    await changeCategoryOnRow('parking', /Transport/);

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(screen.queryByRole('listitem', { name: /parking/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /accept/i })).not.toBeInTheDocument();
  });

  it('makes no change call and leaves the row unchanged when the category picked is the row’s own', async () => {
    const lunch = anExpense({ id: 1, description: 'lunch', categoryId: 10 });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch]));
    listCategoriesMock.mockResolvedValue(twoCategories);

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('lunch', /Groceries/);

    expect(changeCategoryMock).not.toHaveBeenCalled();
    const lunchRow = screen.getByRole('listitem', { name: /lunch/i });
    expect(within(lunchRow).getByText('Groceries')).toBeInTheDocument();
  });

  it('makes only one change call when a category is picked on a second row while one change is still out', async () => {
    const lunch = anExpense({
      id: 1,
      description: 'lunch',
      categoryId: 10,
      createdAt: '2026-08-05T09:00:00Z',
    });
    const coffee = anExpense({
      id: 2,
      description: 'coffee',
      categoryId: 10,
      createdAt: '2026-08-05T10:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch, coffee]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    const outstanding = inFlight<Expense>();
    changeCategoryMock.mockReturnValueOnce(outstanding.promise);

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('lunch', /Transport/);

    // Every other row's control is disabled while a change is out, so the second row's control cannot be
    // opened at all — that disabling is itself what keeps this to one call.
    const coffeeControl = screen.getByRole('button', { name: /change coffee/i });
    expect(coffeeControl).toBeDisabled();
    await userEvent.click(coffeeControl);

    expect(changeCategoryMock).toHaveBeenCalledOnce();
    expect(screen.queryByRole('option', { name: /Transport/i })).not.toBeInTheDocument();
  });

  it('leaves every row’s control usable once the change is answered, and a further pick sends a second call', async () => {
    const lunch = anExpense({
      id: 1,
      description: 'lunch',
      categoryId: 10,
      createdAt: '2026-08-05T09:00:00Z',
    });
    const coffee = anExpense({
      id: 2,
      description: 'coffee',
      categoryId: 10,
      createdAt: '2026-08-05T10:00:00Z',
    });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch, coffee]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockResolvedValueOnce({ ...lunch, categoryId: 20 });
    changeCategoryMock.mockResolvedValueOnce({ ...coffee, categoryId: 20 });

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('lunch', /Transport/);
    await waitFor(() => expect(changeCategoryMock).toHaveBeenCalledOnce());

    for (const control of screen.getAllByRole('button', { name: /change (lunch|coffee)/i })) {
      expect(control).toBeEnabled();
      expect(control).not.toHaveAttribute('aria-busy', 'true');
    }

    await changeCategoryOnRow('coffee', /Transport/);
    expect(changeCategoryMock).toHaveBeenCalledTimes(2);
  });

  it('shows the ledger’s own message under the row, keeps its old category, leaves the page’s banner untouched, reads nothing back and leaves every control usable again, when the ledger refuses with 503', async () => {
    const lunch = anExpense({ id: 1, description: 'lunch', categoryId: 10 });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockRejectedValueOnce(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('lunch', /Transport/);

    const lunchRow = await screen.findByRole('listitem', { name: /lunch/i });
    expect(within(lunchRow).getByText('the ledger is temporarily unavailable')).toBeInTheDocument();
    expect(within(lunchRow).getByText('Groceries')).toBeInTheDocument();
    // The row's own message renders through the same `Alert` (role="alert") as the page's banner, so the
    // banner staying untouched means exactly one alert exists — the row's — not zero.
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(listExpensesMock).toHaveBeenCalledOnce();
    expect(screen.getByRole('button', { name: /change lunch/i })).toBeEnabled();
  });

  it('shows the ledger’s message under the row and reads back the day it was on, when the ledger refuses with 404', async () => {
    const taxi = anExpense({
      id: 3,
      description: 'taxi',
      categoryId: 10,
      createdAt: '2026-08-04T18:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([taxi]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockRejectedValueOnce(new ApiError(404, 'that entry has moved on'));
    // Held open rather than resolved immediately: the reread would otherwise empty the day and drop the row
    // before the test ever gets to look at its message, since nothing here delays the mock's own resolution.
    const reread = inFlight<ExpensePage>();
    listExpensesMock.mockReturnValueOnce(reread.promise);

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('taxi', /Transport/);

    const taxiRow = await screen.findByRole('listitem', { name: /taxi/i });
    expect(within(taxiRow).getByText('that entry has moved on')).toBeInTheDocument();
    expect(listExpensesMock).toHaveBeenCalledTimes(2);
    expect(listExpensesMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ from: '2026-08-04', to: '2026-08-04' }),
    );

    // Now let the reread land, so the stale row leaves as the scenario says it does.
    await act(async () => {
      reread.answer(anExpensePage([]));
    });
    await waitFor(() =>
      expect(screen.queryByRole('listitem', { name: /taxi/i })).not.toBeInTheDocument(),
    );
  });

  it('reports the session expired, re-reads nothing and shows no message on the row, when the ledger refuses with 401', async () => {
    const lunch = anExpense({ id: 1, description: 'lunch', categoryId: 10 });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockRejectedValueOnce(new ApiError(401, 'no session'));
    const sessionExpired = vi.fn();

    renderPage({ sessionExpired });
    await listedEntries();

    await changeCategoryOnRow('lunch', /Transport/);

    await waitFor(() => expect(sessionExpired).toHaveBeenCalledOnce());
    expect(listExpensesMock).toHaveBeenCalledOnce();
    const lunchRow = screen.getByRole('listitem', { name: /lunch/i });
    expect(within(lunchRow).queryByText('no session')).not.toBeInTheDocument();
  });

  it('clears a row’s refusal before the second call’s answer arrives', async () => {
    const lunch = anExpense({ id: 1, description: 'lunch', categoryId: 10 });
    listExpensesMock.mockResolvedValue(anExpensePage([lunch]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    changeCategoryMock.mockRejectedValueOnce(
      new ApiError(503, 'the ledger is temporarily unavailable'),
    );
    const outstanding = inFlight<Expense>();
    changeCategoryMock.mockReturnValueOnce(outstanding.promise);

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('lunch', /Transport/);
    const firstRefusal = await screen.findByRole('listitem', { name: /lunch/i });
    expect(
      within(firstRefusal).getByText('the ledger is temporarily unavailable'),
    ).toBeInTheDocument();

    await changeCategoryOnRow('lunch', /Transport/);

    expect(
      within(screen.getByRole('listitem', { name: /lunch/i })).queryByText(
        'the ledger is temporarily unavailable',
      ),
    ).not.toBeInTheDocument();
  });

  it('reads back against the filter the page holds now, not the one the change call left with, and drops an answer for a row the page no longer holds', async () => {
    const ferry = anExpense({
      id: 25,
      description: 'ferry',
      categoryId: 10,
      createdAt: '2026-08-05T09:00:00Z',
    });
    listExpensesMock.mockResolvedValueOnce(anExpensePage([ferry]));
    listCategoriesMock.mockResolvedValue(twoCategories);
    const outstanding = inFlight<Expense>();
    changeCategoryMock.mockReturnValueOnce(outstanding.promise);

    renderPage();
    await listedEntries();

    await changeCategoryOnRow('ferry', /Transport/);

    listExpensesMock.mockResolvedValueOnce(anExpensePage([ferry]));
    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));

    listExpensesMock.mockResolvedValueOnce(anExpensePage([]));
    await act(async () => {
      outstanding.answer({ ...ferry, categoryId: 20 });
    });

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    expect(listExpensesMock).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 10 }));
  });

  it('moves focus to the day header that held a refiled row the read back removed', async () => {
    const day = '2026-08-05T09:00:00Z';
    const lunch = anExpense({
      id: 50,
      status: 'RECORDED',
      description: 'lunch',
      categoryId: 10,
      createdAt: day,
    });
    const coffee = anExpense({
      id: 51,
      status: 'PENDING',
      description: 'coffee',
      categoryId: 10,
      createdAt: day,
    });
    const narrowed = anExpensePage([lunch, coffee], { limit: 50, offset: 0, total: 2 });
    listExpensesMock.mockResolvedValueOnce(page).mockResolvedValueOnce(narrowed);
    listCategoriesMock.mockResolvedValue(twoCategories);

    renderPage();
    await listedEntries();
    await chooseGroceries();
    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(2));
    expandDays();

    changeCategoryMock.mockResolvedValueOnce({ ...lunch, categoryId: 20 });
    listExpensesMock.mockResolvedValueOnce(
      anExpensePage([coffee], { limit: 50, offset: 0, total: 2 }),
    );

    await changeCategoryOnRow('lunch', /Transport/);

    await waitFor(() => expect(listExpensesMock).toHaveBeenCalledTimes(3));
    // `dayHeaders()` returns only collapsed headers; the surviving day section is expanded (it was opened by
    // `expandDays()` above and stays that way), so its header is found the same way `dayHeaders()` finds a
    // collapsed one, just with the opposite `expanded` value.
    const openHeaders = screen
      .getAllByRole('button', { expanded: true })
      .filter((header) => !header.hasAttribute('aria-haspopup'));
    await waitFor(() => expect(document.activeElement).toEqual(openHeaders[0]));
  });
});

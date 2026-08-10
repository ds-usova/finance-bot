import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { DayTotal, ExpensePage } from '../api/expenses';
import { en } from '../i18n/en';
import { expandDays } from '../testing/accordion';
import { substituteCatalogue } from '../testing/catalogue';
import { chooseFromList } from '../testing/combobox';
import { aCategory, aGrouping, anExpense, anExpensePage, categoryNames } from '../testing/fixtures';
import { ExpenseList, type ExpenseListProps } from './ExpenseList';

/** The list under test, with the props a case says nothing about left inert. */
function renderList(props: Partial<ExpenseListProps> & { page: ExpensePage }) {
  return render(
    <ExpenseList
      categoryNames={categoryNames}
      categories={[]}
      groupings={[]}
      tickedIds={new Set()}
      onTick={vi.fn()}
      onTickDay={vi.fn()}
      tickHeadroom={Infinity}
      changingKey={null}
      changeFailure={null}
      focusDay={null}
      onChangeCategory={vi.fn()}
      {...props}
    />,
  );
}

function threeDaysOfEntries() {
  return [
    anExpense({
      id: 1,
      description: 'coffee',
      money: { amount: '3.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-01T09:00:00Z',
    }),
    anExpense({
      id: 2,
      description: 'lunch',
      money: { amount: '5.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-02T09:00:00Z',
    }),
    anExpense({
      id: 3,
      description: 'taxi',
      money: { amount: '9.00', currency: 'EUR', separator: '' },
      createdAt: '2026-08-03T09:00:00Z',
    }),
  ];
}

/** One recorded entry on each of two UTC days, both filed under the same category: coffee on the 1st, taxi
 * on the 2nd. */
function twoDaysOfEntries() {
  return [
    anExpense({
      id: 1,
      status: 'RECORDED',
      categoryId: 10,
      description: 'coffee',
      createdAt: '2026-08-01T09:00:00Z',
    }),
    anExpense({
      id: 2,
      status: 'RECORDED',
      categoryId: 10,
      description: 'taxi',
      createdAt: '2026-08-02T09:00:00Z',
    }),
  ];
}

/** One pending entry on each of two UTC days: coffee on the 1st, taxi on the 2nd. */
function twoPendingDays() {
  return [
    anExpense({
      id: 1,
      status: 'PENDING',
      description: 'coffee',
      createdAt: '2026-08-01T09:00:00Z',
    }),
    anExpense({
      id: 2,
      status: 'PENDING',
      description: 'taxi',
      createdAt: '2026-08-02T09:00:00Z',
    }),
  ];
}

describe('the expense list', () => {
  it('groups a page of entries recorded across three UTC days into three sections, all closed', () => {
    renderList({ page: anExpensePage(threeDaysOfEntries()) });

    expect(screen.getAllByRole('button')).toHaveLength(3);
    expect(screen.queryByText('coffee')).not.toBeInTheDocument();
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('taxi')).not.toBeInTheDocument();
  });

  it('opens one section on its own, leaving the other two closed', async () => {
    const user = userEvent.setup();

    renderList({ page: anExpensePage(threeDaysOfEntries()) });

    const headers = screen.getAllByRole('button');
    expect(headers).toHaveLength(3);
    // toDaySections orders newest first, so the first header is 2026-08-03's, holding "taxi".
    await user.click(headers[0]!);

    expect(screen.getByText('taxi')).toBeInTheDocument();
    expect(screen.queryByText('lunch')).not.toBeInTheDocument();
    expect(screen.queryByText('coffee')).not.toBeInTheDocument();
  });

  it('shows a day’s own figure in its heading and shows none for a day with no figure', () => {
    const dayTotals: DayTotal[] = [
      { day: '2026-08-03', amounts: [{ amount: '12.34', currency: 'EUR', separator: '' }] },
      { day: '2026-08-01', amounts: [{ amount: '56.78', currency: 'EUR', separator: '' }] },
    ];

    renderList({ page: anExpensePage(threeDaysOfEntries(), { dayTotals }) });

    const headers = screen.getAllByRole('button');
    // toDaySections orders newest first: [0] is 2026-08-03, [1] is 2026-08-02, [2] is 2026-08-01.
    expect(headers[0]).toHaveTextContent('EUR12.34');
    expect(headers[2]).toHaveTextContent('EUR56.78');
    expect(headers[1]).not.toHaveTextContent('EUR');
  });

  it('shows the catalogue’s substituted text for the empty state, once the catalogue is swapped', () => {
    substituteCatalogue();

    renderList({ page: anExpensePage([], { total: 0 }) });

    expect(screen.getByRole('alert')).toHaveTextContent('‹No expenses to show.›');
  });

  it('says there is nothing to show when the page holds no entries', () => {
    renderList({ page: anExpensePage([], { total: 0 }) });

    expect(screen.getByRole('alert')).toHaveTextContent('No expenses to show.');
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('reads a ticked checkbox on one day and not the other, once both are opened', () => {
    renderList({ page: anExpensePage(twoPendingDays()), tickedIds: new Set([1]) });
    expandDays();

    const firstItem = screen.getByRole('listitem', { name: /coffee/i });
    const secondItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(
      within(firstItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeChecked();
    expect(
      within(secondItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).not.toBeChecked();
  });

  it('calls the list’s onTick with the entry’s id and true when its unticked checkbox is clicked', async () => {
    const user = userEvent.setup();
    const onTick = vi.fn();
    renderList({ page: anExpensePage(twoPendingDays()), onTick });
    expandDays();

    const secondItem = screen.getByRole('listitem', { name: /taxi/i });
    const checkbox = within(secondItem).getByRole('checkbox', {
      name: en.listing.entryCheckboxLabel,
    });
    await user.click(checkbox);

    expect(onTick).toHaveBeenCalledWith(2, true);
  });

  it('disables every unticked checkbox on both days at the bound, leaving the ticked one live', () => {
    renderList({
      page: anExpensePage(twoPendingDays()),
      tickedIds: new Set([1]),
      tickHeadroom: 0,
    });
    expandDays();

    const firstItem = screen.getByRole('listitem', { name: /coffee/i });
    const secondItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(
      within(firstItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeEnabled();
    expect(
      within(secondItem).getByRole('checkbox', { name: en.listing.entryCheckboxLabel }),
    ).toBeDisabled();
  });

  it('marks the row a change is out for as busy and disables every control on the other day', () => {
    renderList({ page: anExpensePage(twoDaysOfEntries()), changingKey: 'RECORDED-1' });
    expandDays();

    const busyControl = screen.getByRole('button', { name: 'Change coffee’s category' });
    const otherControl = screen.getByRole('button', { name: 'Change taxi’s category' });
    expect(busyControl).toHaveAttribute('aria-busy', 'true');
    expect(otherControl).toBeDisabled();
  });

  it('calls the list’s onChangeCategory with the row’s entry and the chosen id when a category is picked', async () => {
    const onChangeCategory = vi.fn();
    const groceries = aCategory({ id: 10, name: 'Groceries' });
    const transport = aCategory({
      id: 20,
      name: 'Transport',
      groupingId: groceries.groupingId,
      groupingName: groceries.groupingName,
    });
    const entries = twoDaysOfEntries();

    renderList({
      page: anExpensePage(entries),
      changingKey: null,
      categories: [groceries, transport],
      groupings: [aGrouping({ id: groceries.groupingId, name: groceries.groupingName })],
      onChangeCategory,
    });
    expandDays();

    await chooseFromList('Change taxi’s category', 'Transport');

    expect(onChangeCategory).toHaveBeenCalledWith(entries[1], transport.id);
  });

  it('shows the refusal only on the row it names, leaving the other day without one', () => {
    renderList({
      page: anExpensePage(twoDaysOfEntries()),
      changeFailure: { key: 'RECORDED-2', message: 'That category no longer exists.' },
    });
    expandDays();

    const firstItem = screen.getByRole('listitem', { name: /coffee/i });
    const secondItem = screen.getByRole('listitem', { name: /taxi/i });
    expect(within(secondItem).getByRole('alert')).toHaveTextContent(
      'That category no longer exists.',
    );
    expect(within(firstItem).queryByRole('alert')).not.toBeInTheDocument();
  });
});

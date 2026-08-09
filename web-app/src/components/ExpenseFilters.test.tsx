import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { ExpenseFilter } from '../api/expenses';
import { substituteCatalogue } from '../testing/catalogue';
import { chooseFromList, chooseOption } from '../testing/combobox';
import { aCategory, aGrouping } from '../testing/fixtures';
import { ExpenseFilters } from './ExpenseFilters';

const everyday = aGrouping({ id: 100, name: 'Everyday' });
const travel = aGrouping({ id: 200, name: 'Travel' });

const groceries = aCategory({
  id: 10,
  name: 'Groceries',
  groupingId: everyday.id,
  groupingName: everyday.name,
});
const rent = aCategory({
  id: 11,
  name: 'Rent',
  groupingId: everyday.id,
  groupingName: everyday.name,
});
const flights = aCategory({
  id: 20,
  name: 'Flights',
  groupingId: travel.id,
  groupingName: travel.name,
});

function renderFilters(filter: ExpenseFilter = {}) {
  const onChange = vi.fn();
  const user = userEvent.setup();

  function Harness() {
    const [current, setCurrent] = useState(filter);

    return (
      <ExpenseFilters
        groupings={[everyday, travel]}
        categories={[groceries, rent, flights]}
        filter={current}
        onChange={(next) => {
          onChange(next);
          setCurrent(next);
        }}
      />
    );
  }

  render(<Harness />);

  return { onChange, user };
}

function categoryControl(): HTMLElement {
  return screen.getByRole('button', { name: /^category/i });
}

function statusControl(): HTMLElement {
  return screen.getByRole('combobox', { name: 'Status' });
}

function periodControl(): HTMLElement {
  // Anchored: the control that clears the period is named "Clear the recorded period" and matches otherwise.
  return screen.getByRole('button', { name: /^recorded period/i });
}

/** Clicks a day by its number in one of the two months the calendar shows, the first being the nearer. */
async function chooseDay(user: ReturnType<typeof userEvent.setup>, month: 0 | 1, day: string) {
  const grid = screen.getAllByRole('grid')[month];
  await user.click(within(grid!).getByText(day));
}

afterEach(() => {
  vi.useRealTimers();
});

describe('the filter controls', () => {
  it('offers every category it was given in one list, each by its accessible name', async () => {
    const { user } = renderFilters();

    await user.click(categoryControl());

    expect(await screen.findByRole('option', { name: 'Groceries' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'All' })).toBeInTheDocument();
  });

  it('heads each stretch of the list with the grouping its categories belong to', async () => {
    const { user } = renderFilters();

    await user.click(categoryControl());

    await screen.findByRole('option', { name: 'Groceries' });
    expect(screen.getByText('Everyday')).toBeInTheDocument();
    expect(screen.getByText('Travel')).toBeInTheDocument();
    // A heading names a stretch of the list; it is not something the person can choose.
    expect(screen.queryByRole('option', { name: 'Everyday' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Travel' })).not.toBeInTheDocument();
  });

  it('narrows the list to what the search matches, by category name and by grouping name alike', async () => {
    const { user } = renderFilters();

    await user.click(categoryControl());
    await user.type(await screen.findByPlaceholderText('Search categories'), 'ren');

    expect(screen.getByRole('option', { name: 'Rent' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Flights' })).not.toBeInTheDocument();

    await user.clear(screen.getByPlaceholderText('Search categories'));
    await user.type(screen.getByPlaceholderText('Search categories'), 'travel');

    expect(screen.getByRole('option', { name: 'Flights' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'Rent' })).not.toBeInTheDocument();
  });

  it('says so when the search matches no category', async () => {
    const { user } = renderFilters();

    await user.click(categoryControl());
    await user.type(await screen.findByPlaceholderText('Search categories'), 'zzz');

    expect(screen.getByText('No category found.')).toBeInTheDocument();
  });

  it('clears the category through the list’s own all entry', async () => {
    const { onChange } = renderFilters({ categoryId: flights.id });

    await chooseFromList(/^category/i, 'All');

    expect(onChange).toHaveBeenCalledWith({});
    expect(categoryControl()).toHaveTextContent('All');
  });

  it('calls back with the chosen status, leaving the rest of the filter as it stood', async () => {
    const { onChange } = renderFilters({ categoryId: groceries.id, from: '2026-08-01' });

    await chooseOption('Status', 'Recorded');

    expect(onChange).toHaveBeenCalledWith({
      categoryId: groceries.id,
      from: '2026-08-01',
      status: 'RECORDED',
    });
  });

  it('calls back once with both days, only after the second is picked on the calendar', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-01' });

    await user.click(periodControl());
    await chooseDay(user, 0, '10');
    expect(onChange).not.toHaveBeenCalled();

    await chooseDay(user, 0, '20');

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({ from: '2026-08-10', to: '2026-08-20' });
  });

  it('closes the calendar and names the period it settled on', async () => {
    const { user } = renderFilters({ from: '2026-08-01', to: '2026-08-01' });

    await user.click(periodControl());
    await chooseDay(user, 0, '10');
    await chooseDay(user, 0, '20');

    expect(screen.queryByRole('grid')).not.toBeInTheDocument();
    expect(periodControl()).toHaveTextContent('Aug 10, 2026');
    expect(periodControl()).toHaveTextContent('Aug 20, 2026');
  });

  it('sets both days at once from a preset', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-08-09T10:00:00'));
    const { onChange, user } = renderFilters();

    await user.click(periodControl());
    await user.click(screen.getByRole('button', { name: 'This month' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({ from: '2026-08-01', to: '2026-08-09' });
  });

  it('calls back with the chosen category’s id', async () => {
    const { onChange } = renderFilters();

    await chooseFromList(/^category/i, 'Groceries');

    expect(onChange).toHaveBeenCalledWith({ categoryId: groceries.id });
  });

  it('labels the period by when a row was recorded, not by when the money was spent', () => {
    renderFilters();

    expect(periodControl()).toBeInTheDocument();
    expect(screen.queryByText(/spent/i)).not.toBeInTheDocument();
  });

  it('names no period until one is set', () => {
    renderFilters();

    expect(periodControl()).toHaveTextContent('Any time');
  });

  it('calls back once with neither day when the period is cleared', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-31' });

    await user.click(screen.getByRole('button', { name: /clear the recorded period/i }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({});
    expect(periodControl()).toHaveTextContent('Any time');
  });

  it('offers no way to clear a period that was never set', () => {
    renderFilters();

    expect(
      screen.queryByRole('button', { name: /clear the recorded period/i }),
    ).not.toBeInTheDocument();
  });

  it('clears both days at once from the all-time preset', async () => {
    const { onChange, user } = renderFilters({ from: '2026-08-01', to: '2026-08-31' });

    await user.click(periodControl());
    await user.click(screen.getByRole('button', { name: 'All time' }));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith({});
  });

  it('offers a reset once a filter is set, and clears every one of them', async () => {
    const { onChange, user } = renderFilters();

    expect(screen.queryByRole('button', { name: 'Reset filters' })).not.toBeInTheDocument();

    await chooseOption('Status', 'Recorded');
    await user.click(screen.getByRole('button', { name: 'Reset filters' }));

    expect(onChange).toHaveBeenLastCalledWith({});
    expect(statusControl()).toHaveTextContent('All');
    expect(screen.queryByRole('button', { name: 'Reset filters' })).not.toBeInTheDocument();
  });

  it('reads the status options as labels, Recorded and Pending, rather than the shouted wire value', async () => {
    const { user } = renderFilters();

    await user.click(statusControl());

    expect(await screen.findByRole('option', { name: 'Recorded' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Pending' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'RECORDED' })).not.toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'PENDING' })).not.toBeInTheDocument();
  });

  it('shows the catalogue’s substituted text for every label and every option', async () => {
    substituteCatalogue();

    const { user } = renderFilters();

    expect(screen.getByText('‹Filters›')).toBeInTheDocument();
    expect(screen.getByText('‹Category›')).toBeInTheDocument();
    expect(screen.getByText('‹Status›')).toBeInTheDocument();
    expect(screen.getByText('‹Recorded period›')).toBeInTheDocument();
    expect(screen.getByText('‹Any time›')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /‹Category›/ }));
    expect(await screen.findByPlaceholderText('‹Search categories›')).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '‹All›' })).toBeInTheDocument();
    await user.keyboard('{Escape}');

    await user.click(screen.getByRole('combobox', { name: '‹Status›' }));
    expect(await screen.findByRole('option', { name: '‹Recorded›' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '‹Pending›' })).toBeInTheDocument();
  });
});
